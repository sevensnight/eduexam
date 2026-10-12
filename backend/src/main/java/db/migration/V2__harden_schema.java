package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Upgrades legacy installations whose inline REFERENCES clauses were ignored by
 * MySQL. Orphans are handled deliberately before the real foreign keys are
 * installed, so an existing classroom database can be migrated in place.
 */
public class V2__harden_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ensureColumn(connection, "users", "token_version",
                "ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER is_active");

        execute(connection, "UPDATE exam_records SET status='graded' WHERE status='completed'");
        execute(connection, "UPDATE exam_records SET status='in_progress' WHERE status='started'");
        execute(connection, "UPDATE categories c LEFT JOIN categories p ON p.id=c.parent_id " +
                "SET c.parent_id=NULL WHERE c.parent_id IS NOT NULL AND p.id IS NULL");
        execute(connection, "UPDATE categories c LEFT JOIN users u ON u.id=c.creator_id " +
                "SET c.creator_id=NULL WHERE c.creator_id IS NOT NULL AND u.id IS NULL");
        execute(connection, "UPDATE tags t LEFT JOIN users u ON u.id=t.creator_id " +
                "SET t.creator_id=NULL WHERE t.creator_id IS NOT NULL AND u.id IS NULL");
        execute(connection, "UPDATE questions q LEFT JOIN categories c ON c.id=q.category_id " +
                "SET q.category_id=NULL WHERE q.category_id IS NOT NULL AND c.id IS NULL");
        execute(connection, "UPDATE questions q LEFT JOIN users u ON u.id=q.creator_id " +
                "SET q.creator_id=NULL WHERE q.creator_id IS NOT NULL AND u.id IS NULL");
        execute(connection, "UPDATE courses c LEFT JOIN users u ON u.id=c.teacher_id " +
                "SET c.teacher_id=NULL WHERE c.teacher_id IS NOT NULL AND u.id IS NULL");
        execute(connection, "UPDATE exams e LEFT JOIN users u ON u.id=e.creator_id " +
                "SET e.creator_id=NULL WHERE e.creator_id IS NOT NULL AND u.id IS NULL");
        execute(connection, "UPDATE exams e LEFT JOIN courses c ON c.id=e.course_id " +
                "SET e.course_id=NULL WHERE e.course_id IS NOT NULL AND c.id IS NULL");
        breakCategoryCycles(connection);

        execute(connection, "DELETE a FROM answers a " +
                "LEFT JOIN exam_records r ON r.id=a.record_id " +
                "LEFT JOIN exams e ON e.id=r.exam_id LEFT JOIN users s ON s.id=r.student_id " +
                "LEFT JOIN questions q ON q.id=a.question_id " +
                "WHERE r.id IS NULL OR e.id IS NULL OR s.id IS NULL OR q.id IS NULL");
        execute(connection, "DELETE wb FROM wrong_book wb LEFT JOIN users u ON u.id=wb.student_id " +
                "LEFT JOIN questions q ON q.id=wb.question_id WHERE u.id IS NULL OR q.id IS NULL");
        execute(connection, "DELETE n FROM notifications n LEFT JOIN users u ON u.id=n.user_id WHERE u.id IS NULL");
        execute(connection, "DELETE qt FROM question_tags qt LEFT JOIN questions q ON q.id=qt.question_id " +
                "LEFT JOIN tags t ON t.id=qt.tag_id WHERE q.id IS NULL OR t.id IS NULL");
        execute(connection, "DELETE eq FROM exam_questions eq LEFT JOIN exams e ON e.id=eq.exam_id " +
                "LEFT JOIN questions q ON q.id=eq.question_id WHERE e.id IS NULL OR q.id IS NULL");
        execute(connection, "DELETE ce FROM course_enrollments ce LEFT JOIN courses c ON c.id=ce.course_id " +
                "LEFT JOIN users u ON u.id=ce.student_id WHERE c.id IS NULL OR u.id IS NULL");
        execute(connection, "DELETE r FROM exam_records r LEFT JOIN exams e ON e.id=r.exam_id " +
                "LEFT JOIN users u ON u.id=r.student_id WHERE e.id IS NULL OR u.id IS NULL");
        execute(connection, "DELETE older FROM answers older INNER JOIN answers newer " +
                "ON newer.record_id=older.record_id AND newer.question_id=older.question_id AND newer.id>older.id");
        ensureIndex(connection, "answers", "uq_answer_record_question",
                "ALTER TABLE answers ADD UNIQUE KEY uq_answer_record_question (record_id, question_id)");

        addForeignKey(connection, "fk_categories_parent", "ALTER TABLE categories ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT");
        addForeignKey(connection, "fk_categories_creator", "ALTER TABLE categories ADD CONSTRAINT fk_categories_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL");
        addForeignKey(connection, "fk_tags_creator", "ALTER TABLE tags ADD CONSTRAINT fk_tags_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL");
        addForeignKey(connection, "fk_questions_category", "ALTER TABLE questions ADD CONSTRAINT fk_questions_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL");
        addForeignKey(connection, "fk_questions_creator", "ALTER TABLE questions ADD CONSTRAINT fk_questions_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL");
        addForeignKey(connection, "fk_question_tags_question", "ALTER TABLE question_tags ADD CONSTRAINT fk_question_tags_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_question_tags_tag", "ALTER TABLE question_tags ADD CONSTRAINT fk_question_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_courses_teacher", "ALTER TABLE courses ADD CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL");
        addForeignKey(connection, "fk_enrollments_course", "ALTER TABLE course_enrollments ADD CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_enrollments_student", "ALTER TABLE course_enrollments ADD CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_exams_creator", "ALTER TABLE exams ADD CONSTRAINT fk_exams_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL");
        addForeignKey(connection, "fk_exams_course", "ALTER TABLE exams ADD CONSTRAINT fk_exams_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT");
        addForeignKey(connection, "fk_exam_questions_exam", "ALTER TABLE exam_questions ADD CONSTRAINT fk_exam_questions_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_exam_questions_question", "ALTER TABLE exam_questions ADD CONSTRAINT fk_exam_questions_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE RESTRICT");
        addForeignKey(connection, "fk_exam_records_exam", "ALTER TABLE exam_records ADD CONSTRAINT fk_exam_records_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_exam_records_student", "ALTER TABLE exam_records ADD CONSTRAINT fk_exam_records_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT");
        addForeignKey(connection, "fk_answers_record", "ALTER TABLE answers ADD CONSTRAINT fk_answers_record FOREIGN KEY (record_id) REFERENCES exam_records(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_answers_question", "ALTER TABLE answers ADD CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE RESTRICT");
        addForeignKey(connection, "fk_wrong_book_student", "ALTER TABLE wrong_book ADD CONSTRAINT fk_wrong_book_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_wrong_book_question", "ALTER TABLE wrong_book ADD CONSTRAINT fk_wrong_book_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE");
        addForeignKey(connection, "fk_notifications_user", "ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE");
    }

    private void breakCategoryCycles(Connection connection) throws SQLException {
        Map<Long, Long> parents = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, parent_id FROM categories")) {
            while (rs.next()) parents.put(rs.getLong(1), rs.getObject(2, Long.class));
        }
        Set<Long> repaired = new HashSet<>();
        for (Long start : parents.keySet()) {
            Map<Long, Integer> path = new HashMap<>();
            Long current = start;
            int position = 0;
            while (current != null && parents.containsKey(current)) {
                if (path.putIfAbsent(current, position++) != null) {
                    if (repaired.add(current)) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE categories SET parent_id=NULL WHERE id=?")) {
                            statement.setLong(1, current);
                            statement.executeUpdate();
                        }
                        parents.put(current, null);
                    }
                    break;
                }
                current = parents.get(current);
            }
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String ddl) throws SQLException {
        if (!exists(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                table, column)) execute(connection, ddl);
    }

    private void ensureIndex(Connection connection, String table, String index, String ddl) throws SQLException {
        if (!exists(connection,
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?",
                table, index)) execute(connection, ddl);
    }

    private void addForeignKey(Connection connection, String name, String ddl) throws SQLException {
        if (!exists(connection,
                "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name=?",
                name)) execute(connection, ddl);
    }

    private boolean exists(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
