package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/** Adds an optional in-app destination to persisted notifications. */
public class V6__add_notification_target_path extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (columnExists(connection, "notifications", "target_path")) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE notifications " +
                    "ADD COLUMN target_path VARCHAR(255) NULL AFTER type");
        }
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
}
