package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adds the compare-and-set revision used by draft autosaves to installations
 * that were baselined from an older schema instead of executing V1.
 */
public class V4__ensure_exam_draft_version extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!columnExists(connection, "exam_records", "version")) {
            execute(connection, "ALTER TABLE exam_records " +
                    "ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER total_score");
            return;
        }

        execute(connection, "UPDATE exam_records SET version=1 WHERE version IS NULL OR version < 1");
        execute(connection, "ALTER TABLE exam_records MODIFY COLUMN version INT NOT NULL DEFAULT 1");
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getLong(1) > 0;
            }
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
