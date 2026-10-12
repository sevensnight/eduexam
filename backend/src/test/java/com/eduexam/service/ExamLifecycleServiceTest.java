package com.eduexam.service;

import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamLifecycleServiceTest {

    @Mock private ExamMapper examMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private AnswerMapper answerMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private UserMapper userMapper;
    @Mock private CourseEnrollmentMapper enrollmentMapper;
    @Mock private NotificationMapper notificationMapper;

    private ExamLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ExamLifecycleService(examMapper, examQuestionMapper, examRecordMapper,
                answerMapper, questionMapper, userMapper, enrollmentMapper, notificationMapper);
    }

    @Test
    void refusesToPublishAnEmptyScheduledExam() {
        Exam exam = exam("draft");
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of());

        assertThrows(AppException.class, () -> service.publishExam(1L));
        assertEquals("draft", exam.getStatus());
        verify(examMapper, never()).updateById(any(Exam.class));
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void publicationNotifiesOnlyStudentsMatchingCourseAndClassIntersection() {
        Exam exam = exam("draft");
        exam.setCourseId(9L);
        exam.setTargetClass("软件一班");
        ExamQuestion eq = examQuestion();
        Question question = question();
        User eligible = student(10L);
        User notEnrolled = student(11L);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(9L);
        enrollment.setStudentId(10L);

        when(examMapper.selectOne(any())).thenReturn(exam);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(eq));
        when(questionMapper.selectById(3L)).thenReturn(question);
        when(userMapper.selectList(any())).thenReturn(List.of(eligible, notEnrolled));
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        assertTrue(service.publishExam(1L));

        assertEquals("published", exam.getStatus());
        assertEquals(5.0, exam.getTotalScore());
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notification.capture());
        assertEquals(10L, notification.getValue().getUserId());
    }

    @Test
    void refusesToPublishLegacyChoiceQuestionWithMissingAnswerOption() {
        Exam exam = exam("draft");
        Question malformed = question();
        malformed.setOptions("[\"A. 选项一\",\"B. 选项二\"]");
        malformed.setAnswer("C");
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(3L)).thenReturn(malformed);

        assertThrows(AppException.class, () -> service.publishExam(1L));

        assertEquals("draft", exam.getStatus());
        verify(examMapper, never()).updateById(any(Exam.class));
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void closingExamFinalizesLastServerDraftBeforeChangingStatus() {
        Exam exam = exam("published");
        ExamRecord record = new ExamRecord();
        record.setId(20L);
        record.setExamId(1L);
        record.setStudentId(10L);
        record.setStatus("in_progress");
        record.setStartedAt(LocalDateTime.now().minusMinutes(10));
        Answer draft = new Answer();
        draft.setRecordId(20L);
        draft.setQuestionId(3L);
        draft.setUserAnswer("A");

        when(examMapper.selectOne(any())).thenReturn(exam);
        when(examRecordMapper.selectList(any())).thenReturn(List.of(record), List.of(record));
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(3L)).thenReturn(question());
        when(answerMapper.selectList(any())).thenReturn(List.of(draft));

        assertTrue(service.closeExam(1L));

        assertEquals("closed", exam.getStatus());
        assertEquals("graded", record.getStatus());
        assertEquals(5.0, record.getTotalScore());
        assertTrue(record.getEssayGraded());
        ArgumentCaptor<Answer> persisted = ArgumentCaptor.forClass(Answer.class);
        verify(answerMapper).insert(persisted.capture());
        assertEquals("A", persisted.getValue().getUserAnswer());
        assertEquals("Y", persisted.getValue().getIsCorrect());
        assertEquals(5.0, persisted.getValue().getScoreGot());
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void repeatedLifecycleCallsDoNotDuplicateTransitionNotifications() {
        Exam published = exam("published");
        when(examMapper.selectOne(any())).thenReturn(published);
        when(examRecordMapper.selectList(any())).thenReturn(List.of());

        assertFalse(service.publishExam(1L));
        assertTrue(service.closeExam(1L));
        assertEquals("closed", published.getStatus());

        reset(notificationMapper);
        when(examMapper.selectOne(any())).thenReturn(published);
        when(examRecordMapper.selectList(any())).thenReturn(List.of());
        assertFalse(service.closeExam(1L));
        verifyNoInteractions(notificationMapper);
    }

    private Exam exam(String status) {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setTitle("期末考试");
        exam.setStatus(status);
        exam.setTimeLimit(60);
        exam.setTotalScore(5.0);
        return exam;
    }

    private ExamQuestion examQuestion() {
        ExamQuestion eq = new ExamQuestion();
        eq.setExamId(1L);
        eq.setQuestionId(3L);
        eq.setOrderNum(1);
        eq.setScore(5.0);
        return eq;
    }

    private Question question() {
        Question question = new Question();
        question.setId(3L);
        question.setTitle("示例单选题");
        question.setQuestionType("single");
        question.setDifficulty("medium");
        question.setOptions("[\"A. 正确\",\"B. 错误\"]");
        question.setAnswer("A");
        return question;
    }

    private User student(long id) {
        User student = new User();
        student.setId(id);
        student.setRole("student");
        student.setIsActive(true);
        student.setClassName("软件一班");
        return student;
    }
}
