package com.eduexam.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class QuestionOptionsSqlTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MIGRATION = "db/migration/V5__repair_seed_question_options.sql";
    private static final List<Integer> REPAIRED_IDS = List.of(
            1, 2, 3, 4, 6, 10, 15, 16, 18, 27, 28, 30, 31, 32, 34, 35, 37, 38);

    @Test
    void bothSeedCopiesStayIdentical() throws Exception {
        assertEquals(Files.readString(Path.of("../sql/data.sql"), StandardCharsets.UTF_8), resource("sql/data.sql"));
    }

    @Test
    void questionSeedsUseLiteralUnicodeInsteadOfMysqlSensitiveEscapes() throws Exception {
        String questions = String.join("\n", questionInserts());
        assertFalse(Pattern.compile("\\\\u[0-9a-fA-F]{4}").matcher(questions).find(),
                "SQL 中的单反斜杠 Unicode 转义会被 MySQL 吞掉，应保存为 UTF-8 字符");
        assertTrue(questions.contains("[\"A. 选择\", \"B. 投影\", \"C. 连接\", \"D. 排序\"]"));
        assertTrue(questions.contains("O(n²)"));
    }

    @Test
    void repairIsLimitedToExactKnownSeedRowsAndOnlyOptions() throws Exception {
        String sql = resource(MIGRATION);
        String guards = "(?s)UPDATE questions\\s+SET options = '[^']*', updated_at = updated_at\\s+"
                + "WHERE id = (\\d+)\\s+AND CAST\\(title AS BINARY\\) = CAST\\('[^']*' AS BINARY\\)\\s+"
                + "AND CAST\\(options AS BINARY\\) = CAST\\('[^']*' AS BINARY\\);";
        var matcher = Pattern.compile(guards).matcher(sql);
        List<Integer> ids = new ArrayList<>();
        while (matcher.find()) ids.add(Integer.valueOf(matcher.group(1)));
        assertEquals(REPAIRED_IDS, ids);
        String remaining = sql.replaceAll("(?m)^--[^\\r\\n]*", "").replaceAll(guards, "").trim();
        assertEquals("", remaining, "修复迁移不能包含其他写操作或放宽匹配条件");
    }

    /**
     * 显式启用的真实 MySQL 回归测试。只写当前连接的临时 questions 表，
     * 不创建/删除数据库，不修改已有表或 Flyway 历史；连接关闭后自动清理。
     */
    @ParameterizedTest
    @ValueSource(strings = {"STRICT_TRANS_TABLES", "STRICT_TRANS_TABLES,NO_BACKSLASH_ESCAPES"})
    @EnabledIfEnvironmentVariable(named = "EDUEXAM_TEST_MYSQL_URL", matches = ".+")
    void mysqlImportRepairAndGuardsAreSafe(String sqlMode) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                System.getenv("EDUEXAM_TEST_MYSQL_URL"),
                System.getenv("EDUEXAM_TEST_MYSQL_USER"),
                System.getenv("EDUEXAM_TEST_MYSQL_PASSWORD"));
             Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION sql_mode='" + sqlMode + "'");
            statement.execute("CREATE TEMPORARY TABLE questions ("
                    + "id BIGINT PRIMARY KEY, title TEXT NOT NULL, question_type VARCHAR(16), difficulty VARCHAR(16), "
                    + "options TEXT, answer TEXT, explanation TEXT, usage_count INT, category_id BIGINT, creator_id BIGINT, "
                    + "created_at DATETIME, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            for (String insert : questionInserts()) statement.executeUpdate(insert);

            List<QuestionRow> original = snapshot(connection);
            assertEquals(109, original.size());
            for (QuestionRow row : original) {
                if (row.options() == null) continue;
                JsonNode options = JSON.readTree(row.options());
                assertTrue(options.isArray());
                for (JsonNode option : options) assertTrue(option.isTextual());
                assertFalse(Pattern.compile("u[0-9a-fA-F]{4}").matcher(row.options()).find());
            }
            assertEquals(List.of("A. 选择", "B. 投影", "C. 连接", "D. 排序"),
                    JSON.readValue(original.get(0).options(), List.class));

            // 正常的新库不应被迁移改动。
            migrate(connection);
            assertEquals(original, snapshot(connection));

            // 精确模拟旧 SQL 导入时丢掉反斜杠的 18 道题，包括中文与平方符号。
            for (QuestionRow row : original) {
                if (REPAIRED_IDS.contains(row.id())) updateOptions(connection, row.id(), corrupt(row.options()));
            }
            assertEquals(18, changedOptions(original, snapshot(connection)));
            migrate(connection);
            assertEquals(original, snapshot(connection), "必须恢复全部选项，且其他字段逐个保持不变");
            migrate(connection);
            assertEquals(original, snapshot(connection), "重复执行不能产生额外修改");

            // 真实用户修改、同样文本但不同题号、题干修改、NULL 和合法 JSON 转义均不能被覆盖。
            updateOptions(connection, 1, corrupt(original.get(0).options()).replace("A. ", "A. 修改过的选项 "));
            statement.executeUpdate("UPDATE questions SET id=1002, updated_at=updated_at WHERE id=2");
            updateOptions(connection, 1002, corrupt(original.get(1).options()));
            updateOptions(connection, 3, corrupt(original.get(2).options()));
            statement.executeUpdate("UPDATE questions SET title='用户自定义题目', updated_at=updated_at WHERE id=3");
            updateOptions(connection, 4, null);
            QuestionRow question6 = original.stream().filter(row -> row.id() == 6).findFirst().orElseThrow();
            updateOptions(connection, 6, escaped(question6.options()));
            updateOptions(connection, 10, corrupt(original.get(9).options()).replace("INNER JOIN", "inner join"));
            QuestionRow question15 = original.stream().filter(row -> row.id() == 15).findFirst().orElseThrow();
            updateOptions(connection, 15, corrupt(question15.options()) + " ");
            List<QuestionRow> modified = snapshot(connection);
            migrate(connection);
            assertEquals(modified, snapshot(connection), "匹配须精确到大小写和空格，不能覆盖用户修改");
        }
    }

    private static void migrate(Connection connection) {
        ScriptUtils.executeSqlScript(connection,
                new EncodedResource(new ClassPathResource(MIGRATION), StandardCharsets.UTF_8));
    }

    private static void updateOptions(Connection connection, int id, String options) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE questions SET options=?, updated_at=updated_at WHERE id=?")) {
            statement.setString(1, options);
            statement.setInt(2, id);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static List<QuestionRow> snapshot(Connection connection) throws Exception {
        List<QuestionRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT id, options, JSON_ARRAY(title,question_type,difficulty,answer,explanation,usage_count,"
                        + "category_id,creator_id,created_at,updated_at) AS metadata FROM questions ORDER BY id")) {
            while (result.next()) rows.add(new QuestionRow(result.getInt("id"), result.getString("options"),
                    result.getString("metadata")));
        }
        return rows;
    }

    private static long changedOptions(List<QuestionRow> before, List<QuestionRow> after) {
        return after.stream().filter(row -> !before.contains(row)).count();
    }

    private static String corrupt(String options) {
        return escaped(options).replace("\\", "");
    }

    private static String escaped(String options) {
        StringBuilder result = new StringBuilder();
        for (char character : options.toCharArray()) {
            if (character > 127) result.append('\\').append(String.format("u%04x", (int) character));
            else result.append(character);
        }
        return result.toString();
    }

    private static List<String> questionInserts() throws Exception {
        List<String> inserts = new ArrayList<>();
        StringBuilder current = null;
        for (String line : resource("sql/data.sql").lines().toList()) {
            if (line.startsWith("INSERT INTO `questions` ")) current = new StringBuilder();
            if (current == null) continue;
            current.append(line).append('\n');
            if (line.endsWith(";")) {
                inserts.add(current.toString());
                current = null;
            }
        }
        assertEquals(2, inserts.size());
        assertNull(current, "题目种子 SQL 必须完整结束");
        return inserts;
    }

    private static String resource(String name) throws Exception {
        try (var input = new ClassPathResource(name).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record QuestionRow(int id, String options, String metadata) {}
}
