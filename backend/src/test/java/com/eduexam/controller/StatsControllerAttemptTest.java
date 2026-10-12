package com.eduexam.controller;

import com.eduexam.domain.Exam;
import com.eduexam.domain.ExamRecord;
import com.eduexam.domain.User;
import com.eduexam.mapper.*;
import com.eduexam.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsControllerAttemptTest {

    @Mock private ExamMapper examMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private AnswerMapper answerMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private UserMapper userMapper;
    @Mock private QuestionTagMapper questionTagMapper;
    @Mock private CourseEnrollmentMapper enrollmentMapper;

    private StatsController controller;

    @BeforeEach
    void setUp() {
        controller = new StatsController(examMapper, examQuestionMapper, examRecordMapper,
                answerMapper, questionMapper, categoryMapper, userMapper, questionTagMapper,
                enrollmentMapper);
    }

    @Test
    void summaryUsesBestGradedAttemptPerStudentAndReportsAttemptCountSeparately() {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setTeacherId(7L);
        exam.setStatus("closed");
        exam.setTotalScore(100.0);
        exam.setPassScore(60.0);
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(examRecordMapper.selectCount(any())).thenReturn(3L);
        when(examRecordMapper.findBestGradedAttempts(1L, 10_001)).thenReturn(List.of(
                graded(11L, 1L, 90.0, 2),
                graded(20L, 2L, 70.0, 1)));

        Map<String, Object> result = controller.examSummary(1L, teacher());

        assertEquals(2, result.get("total_participants"));
        assertEquals(2, result.get("unique_participants"));
        assertEquals(3L, result.get("total_attempts"));
        assertEquals(80.0, result.get("avg_score"));
        assertEquals(90.0, result.get("max_score"));
    }

    private ExamRecord graded(long id, long studentId, double score, int attempt) {
        ExamRecord record = new ExamRecord();
        record.setId(id);
        record.setExamId(1L);
        record.setStudentId(studentId);
        record.setStatus("graded");
        record.setTotalScore(score);
        record.setAttemptCount(attempt);
        record.setSubmittedAt(LocalDateTime.now().minusMinutes(id));
        return record;
    }

    private LoginUser teacher() {
        User user = new User();
        user.setId(7L);
        user.setRole("teacher");
        user.setIsActive(true);
        return new LoginUser(user);
    }
}
