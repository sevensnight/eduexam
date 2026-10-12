-- Bring legacy/cached counters back in sync with the authoritative join table.
UPDATE questions q
LEFT JOIN (
    SELECT question_id, COUNT(*) AS usage_count
    FROM exam_questions
    GROUP BY question_id
) actual ON actual.question_id = q.id
SET q.usage_count = COALESCE(actual.usage_count, 0);
