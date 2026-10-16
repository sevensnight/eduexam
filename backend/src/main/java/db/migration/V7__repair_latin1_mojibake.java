package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repairs UTF-8 text that an old MySQL import connection decoded as MySQL
 * latin1 (Windows-1252) before storing it in utf8mb4 columns.
 *
 * <p>The conversion is deliberately conservative. Only contiguous runs whose
 * Unicode code points map back to MySQL latin1 bytes and form valid UTF-8 are
 * decoded. Splitting the value into runs also repairs generated messages that
 * combine correct Chinese text with an already-corrupted title. Correct
 * Chinese, emoji and ordinary accented text remain untouched. Updates compare
 * the original bytes in the WHERE clause so a concurrent edit cannot be
 * replaced.</p>
 */
public class V7__repair_latin1_mojibake extends BaseJavaMigration {
    private static final Logger LOG = LoggerFactory.getLogger(V7__repair_latin1_mojibake.class);

    static final List<TextColumn> COLUMNS = List.of(
            new TextColumn("users", "real_name", true),
            new TextColumn("users", "class_name", true),
            new TextColumn("categories", "name", false),
            new TextColumn("categories", "description", false),
            new TextColumn("tags", "name", true),
            new TextColumn("questions", "title", true),
            new TextColumn("questions", "options", true),
            new TextColumn("questions", "answer", true),
            new TextColumn("questions", "explanation", true),
            new TextColumn("courses", "name", true),
            new TextColumn("courses", "description", true),
            new TextColumn("exams", "title", true),
            new TextColumn("exams", "description", true),
            new TextColumn("exams", "target_class", true),
            new TextColumn("answers", "user_answer", false),
            new TextColumn("notifications", "content", false)
    );

    /** Reverse mapping for the printable characters in MySQL's latin1/cp1252. */
    private static final Map<Integer, Integer> CP1252_BYTES = Map.ofEntries(
            Map.entry(0x20AC, 0x80), Map.entry(0x201A, 0x82), Map.entry(0x0192, 0x83),
            Map.entry(0x201E, 0x84), Map.entry(0x2026, 0x85), Map.entry(0x2020, 0x86),
            Map.entry(0x2021, 0x87), Map.entry(0x02C6, 0x88), Map.entry(0x2030, 0x89),
            Map.entry(0x0160, 0x8A), Map.entry(0x2039, 0x8B), Map.entry(0x0152, 0x8C),
            Map.entry(0x017D, 0x8E), Map.entry(0x2018, 0x91), Map.entry(0x2019, 0x92),
            Map.entry(0x201C, 0x93), Map.entry(0x201D, 0x94), Map.entry(0x2022, 0x95),
            Map.entry(0x2013, 0x96), Map.entry(0x2014, 0x97), Map.entry(0x02DC, 0x98),
            Map.entry(0x2122, 0x99), Map.entry(0x0161, 0x9A), Map.entry(0x203A, 0x9B),
            Map.entry(0x0153, 0x9C), Map.entry(0x017E, 0x9E), Map.entry(0x0178, 0x9F)
    );

    @Override
    public void migrate(Context context) throws Exception {
        int total = 0;
        for (TextColumn column : COLUMNS) {
            int repaired = repairColumn(context.getConnection(), column);
            total += repaired;
            if (repaired > 0) {
                LOG.info("Repaired {} mojibake value(s) in {}.{}", repaired, column.table(), column.name());
            }
        }
        LOG.info("Mojibake repair completed; {} value(s) changed", total);
    }

    static int repairColumn(Connection connection, TextColumn column) throws Exception {
        List<TextRepair> repairs = new ArrayList<>();
        String selectSql = "SELECT `id`, `" + column.name() + "` FROM `" + column.table()
                + "` WHERE `" + column.name() + "` IS NOT NULL";
        try (PreparedStatement statement = connection.prepareStatement(selectSql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String original = result.getString(2);
                String repaired = repairText(original);
                if (!original.equals(repaired)) {
                    repairs.add(new TextRepair(result.getLong(1), original, repaired));
                }
            }
        }

        if (repairs.isEmpty()) return 0;
        String preserveTimestamp = column.preserveUpdatedAt() ? ", `updated_at` = `updated_at`" : "";
        String updateSql = "UPDATE `" + column.table() + "` SET `" + column.name() + "` = ?"
                + preserveTimestamp + " WHERE `id` = ? AND CAST(`" + column.name()
                + "` AS BINARY) = CAST(? AS BINARY)";
        int changed = 0;
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            for (TextRepair repair : repairs) {
                statement.setString(1, repair.repaired());
                statement.setLong(2, repair.id());
                statement.setString(3, repair.original());
                changed += statement.executeUpdate();
            }
        }
        return changed;
    }

    static String repairText(String value) {
        if (value == null || value.isEmpty()) return value;

        int[] codePoints = value.codePoints().toArray();
        StringBuilder result = new StringBuilder(value.length());
        int index = 0;
        while (index < codePoints.length) {
            if (mysqlLatin1Byte(codePoints[index]) == null) {
                // Correct CJK, emoji and most other real Unicode text reaches
                // this branch and is copied without conversion.
                result.appendCodePoint(codePoints[index++]);
                continue;
            }

            int start = index;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (index < codePoints.length) {
                Integer latin1Byte = mysqlLatin1Byte(codePoints[index]);
                if (latin1Byte == null) break;
                bytes.write(latin1Byte);
                index++;
            }

            String originalRun = new String(codePoints, start, index - start);
            result.append(decodeUtf8(bytes.toByteArray(), originalRun));
        }

        String repaired = result.toString();
        return repaired.equals(value) ? value : repaired;
    }

    private static String decodeUtf8(byte[] bytes, String fallback) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            // A genuine latin1/Western-European value is not valid UTF-8 when
            // reversed, so it is not mojibake and must remain unchanged.
            return fallback;
        }
    }

    private static Integer mysqlLatin1Byte(int codePoint) {
        Integer cp1252 = CP1252_BYTES.get(codePoint);
        if (cp1252 != null) return cp1252;
        return codePoint >= 0 && codePoint <= 0xFF ? codePoint : null;
    }

    record TextColumn(String table, String name, boolean preserveUpdatedAt) {}

    private record TextRepair(long id, String original, String repaired) {}
}
