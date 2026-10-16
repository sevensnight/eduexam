package com.eduexam.controller;

import com.eduexam.domain.Answer;
import com.eduexam.domain.Exam;
import com.eduexam.domain.ExamQuestion;
import com.eduexam.domain.ExamRecord;
import com.eduexam.domain.Notification;
import com.eduexam.domain.Question;
import com.eduexam.domain.User;
import com.eduexam.common.AppException;
import com.eduexam.dto.AnswerItemDTO;
import com.eduexam.dto.ExamSubmitDTO;
import com.eduexam.dto.ExamUpdateDTO;
import com.eduexam.mapper.AnswerMapper;
import com.eduexam.mapper.CourseEnrollmentMapper;
import com.eduexam.mapper.CourseMapper;
import com.eduexam.mapper.ExamMapper;
import com.eduexam.mapper.ExamQuestionMapper;
import com.eduexam.mapper.ExamRecordMapper;
import com.eduexam.mapper.NotificationMapper;
import com.eduexam.mapper.QuestionMapper;
import com.eduexam.mapper.QuestionTagMapper;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import com.eduexam.service.ExamLifecycleService;
import com.eduexam.service.ExcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ExamControllerDraftTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock private ExamMapper examMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private AnswerMapper answerMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionTagMapper questionTagMapper;
    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private CourseEnrollmentMapper enrollmentMapper;
    @Mock private NotificationMapper notificationMapper;
    @Mock private ExcelService excelService;
    @Mock private MultipartFile multipartFile;

    private ExamController controller;

    @BeforeEach
    void setUp() {
        ExamLifecycleService lifecycleService = new ExamLifecycleService(examMapper, examQuestionMapper,
                examRecordMapper, answerMapper, questionMapper, userMapper, enrollmentMapper,
                notificationMapper);
        controller = new ExamController(examMapper, examQuestionMapper, examRecordMapper,
                answerMapper, questionMapper, questionTagMapper, userMapper, courseMapper,
                enrollmentMapper, notificationMapper, excelService, lifecycleService);
    }

    @Test
    void savesDraftWithoutGradingOrRevealingCorrectness() {
        Exam exam = exam("published", true);
        ExamRecord record = inProgressRecord();
        ExamQuestion examQuestion = examQuestion();
        Question question = singleChoiceQuestion();
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(loginUser().getUser());
        when(examRecordMapper.selectOne(any())).thenReturn(record);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion));
        when(questionMapper.selectById(4L)).thenReturn(question);

        Map<String, Object> result = controller.saveExamDraft(1L, submission("B"), loginUser());

        ArgumentCaptor<Answer> saved = ArgumentCaptor.forClass(Answer.class);
        verify(answerMapper).insert(saved.capture());
        assertEquals("B", saved.getValue().getUserAnswer());
        assertNull(saved.getValue().getIsCorrect());
        assertEquals(0.0, saved.getValue().getScoreGot());
        assertEquals(true, result.get("ok"));
        assertEquals(2, result.get("draft_version"));
        verify(examRecordMapper).updateById(record);
    }

    @Test
    void rejectsStaleDraftVersionBeforeReplacingAnswers() {
        Exam exam = exam("published", true);
        ExamRecord record = inProgressRecord();
        record.setVersion(2);
        ExamSubmitDTO stale = submission("B");
        stale.setDraftVersion(1);
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(loginUser().getUser());
        when(examRecordMapper.selectOne(any())).thenReturn(record);

        AppException error = assertThrows(AppException.class,
                () -> controller.saveExamDraft(1L, stale, loginUser()));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatus());
        verify(answerMapper, never()).delete(any());
        verify(answerMapper, never()).insert(any(Answer.class));
    }

    @Test
    void rejectsStaleFinalSubmissionBeforeReplacingAnswers() {
        Exam exam = exam("published", true);
        ExamRecord record = inProgressRecord();
        record.setVersion(2);
        ExamSubmitDTO stale = submission("B");
        stale.setDraftVersion(1);
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(loginUser().getUser());
        when(examRecordMapper.selectOne(any())).thenReturn(record);

        AppException error = assertThrows(AppException.class,
                () -> controller.submitExam(1L, stale, loginUser()));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatus());
        verify(answerMapper, never()).delete(any());
        verify(answerMapper, never()).insert(any(Answer.class));
        assertEquals("in_progress", record.getStatus());
    }

    @Test
    void objectiveSubmissionNotifiesExamOwnerOnlyOnce() {
        Exam exam = exam("published", true);
        exam.setTeacherId(8L);
        ExamRecord record = inProgressRecord();
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(loginUser().getUser());
        when(examRecordMapper.selectOne(any())).thenReturn(record);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(4L)).thenReturn(singleChoiceQuestion());

        Map<String, Object> first = controller.submitExam(1L, submission("A"), loginUser());
        Map<String, Object> replay = controller.submitExam(1L, submission("A"), loginUser());

        assertEquals("graded", first.get("status"));
        assertEquals(true, replay.get("already_submitted"));
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, times(1)).insert(notification.capture());
        assertEquals(8L, notification.getValue().getUserId());
        assertEquals("submission", notification.getValue().getType());
        assertFalse(notification.getValue().getIsRead());
        assertTrue(notification.getValue().getContent().contains("学生“student”已提交考试《测试考试》"));
        assertTrue(notification.getValue().getContent().contains("系统已完成客观题评分"));
    }

    @Test
    void essaySubmissionTellsExamOwnerThatReviewIsRequired() {
        Exam exam = exam("published", true);
        exam.setTeacherId(8L);
        ExamRecord record = inProgressRecord();
        User student = loginUser().getUser();
        student.setRealName("张三");
        Question essay = singleChoiceQuestion();
        essay.setQuestionType("essay");
        essay.setAnswer("参考答案");
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(student);
        when(examRecordMapper.selectOne(any())).thenReturn(record);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(4L)).thenReturn(essay);

        Map<String, Object> result = controller.submitExam(1L, submission("学生作答"), loginUser());

        assertEquals("submitted", result.get("status"));
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notification.capture());
        assertEquals(8L, notification.getValue().getUserId());
        assertTrue(notification.getValue().getContent().contains("学生“张三”已提交考试《测试考试》"));
        assertTrue(notification.getValue().getContent().contains("含简答题，请及时批改"));
    }

    @Test
    void expiredSubmissionIsGradedFromLastServerDraft() {
        Exam exam = exam("closed", true);
        ExamRecord record = inProgressRecord();
        ExamQuestion examQuestion = examQuestion();
        Question question = singleChoiceQuestion();
        Answer draft = new Answer();
        draft.setRecordId(2L);
        draft.setQuestionId(4L);
        draft.setUserAnswer("A");
        draft.setScoreGot(0.0);

        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(loginUser().getUser());
        when(examRecordMapper.selectOne(any())).thenReturn(record);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion));
        when(questionMapper.selectById(4L)).thenReturn(question);
        when(answerMapper.selectList(any())).thenReturn(List.of(draft));

        Map<String, Object> result = controller.submitExam(1L, submission("B"), loginUser());

        ArgumentCaptor<Answer> finalized = ArgumentCaptor.forClass(Answer.class);
        verify(answerMapper).insert(finalized.capture());
        assertEquals("A", finalized.getValue().getUserAnswer());
        assertEquals("Y", finalized.getValue().getIsCorrect());
        assertEquals(5.0, finalized.getValue().getScoreGot());
        assertEquals("graded", record.getStatus());
        assertEquals(5.0, record.getTotalScore());
        assertTrue((Boolean) result.get("timed_out"));
        assertEquals(5.0, result.get("total_score"));

        Map<String, Object> replay = controller.submitExam(1L, submission("B"), loginUser());
        assertEquals(true, replay.get("already_submitted"));
        assertEquals(5.0, replay.get("total_score"));
        verify(answerMapper, times(1)).insert(any(Answer.class));
    }

    @Test
    void studentExamMetadataNeverContainsQuestionsBeforeStart() {
        Exam exam = exam("published", true);
        User student = loginUser().getUser();
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(userMapper.selectById(3L)).thenReturn(student);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(singleChoiceQuestion()));

        Map<String, Object> result = controller.getExam(1L, loginUser());

        assertEquals("测试考试", result.get("title"));
        assertEquals(1, result.get("question_count"));
        assertFalse(result.containsKey("questions"));
        assertFalse(result.containsKey("exam_questions"));
    }

    @Test
    void resumedAttemptDoesNotRecheckChangedClassMembership() {
        Exam exam = exam("published", true);
        exam.setTargetClass("原班级");
        ExamRecord record = inProgressRecord();
        User movedStudent = loginUser().getUser();
        movedStudent.setClassName("新班级");
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(userMapper.selectOne(any())).thenReturn(movedStudent);
        when(examRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(4L)).thenReturn(singleChoiceQuestion());
        when(answerMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = controller.startExam(1L, loginUser());

        assertEquals(2L, result.get("record_id"));
        assertEquals("in_progress", result.get("status"));
        assertEquals(1, ((List<?>) result.get("questions")).size());
        verify(examRecordMapper, never()).insert(any(ExamRecord.class));
    }

    @Test
    void myRecordReturnsNewestInProgressRetakeInsteadOfOlderCompletedAttempt() {
        Exam exam = exam("published", true);
        ExamRecord current = inProgressRecord();
        current.setId(30L);
        current.setAttemptCount(2);
        current.setStartedAt(LocalDateTime.now(APP_ZONE));
        ExamRecord previous = inProgressRecord();
        previous.setId(20L);
        previous.setStatus("graded");
        previous.setAttemptCount(1);
        previous.setStartedAt(LocalDateTime.now(APP_ZONE).minusDays(1));
        previous.setSubmittedAt(LocalDateTime.now(APP_ZONE).minusDays(1));
        previous.setTotalScore(5.0);
        previous.setEssayGraded(true);
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(examRecordMapper.selectList(any())).thenReturn(List.of(current, previous));
        when(examQuestionMapper.selectList(any())).thenReturn(List.of());
        when(answerMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = controller.myRecord(1L, loginUser());

        assertEquals(30L, result.get("id"));
        assertEquals("in_progress", result.get("status"));
        assertEquals(2, result.get("attempt_count"));
    }

    @Test
    void closedExamCannotReturnToDraftEvenWhenItHasNoRecords() {
        Exam exam = exam("closed", true);
        ExamUpdateDTO update = new ExamUpdateDTO();
        update.setStatus("draft");
        when(examMapper.selectOne(any())).thenReturn(exam);

        AppException error = assertThrows(AppException.class,
                () -> controller.updateExam(1L, update, teacherUser()));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("closed", exam.getStatus());
    }

    @Test
    void gradeImportRejectsTotalThatDoesNotEqualPerQuestionScores() throws Exception {
        Exam exam = exam("closed", true);
        ExamRecord record = inProgressRecord();
        record.setStatus("graded");
        record.setTotalScore(5.0);
        record.setEssayGraded(true);
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(4L)).thenReturn(singleChoiceQuestion());
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("grades.xlsx");
        when(multipartFile.getBytes()).thenReturn(new byte[]{1});
        when(excelService.parse(any(), any())).thenReturn(List.of(Map.of(
                "record_id", "2", "student_id", "3",
                "question_4_score", "2", "total_score", "5")));
        when(examRecordMapper.selectById(2L)).thenReturn(record);

        Map<String, Object> result = controller.importGrades(1L, multipartFile, teacherUser());

        assertEquals(0, result.get("updated"));
        assertEquals(1, ((List<?>) result.get("errors")).size());
        verify(answerMapper, never()).updateById(any(Answer.class));
    }

    @Test
    void gradeImportUpdatesAnswersAndDerivesTotalAndCorrectnessTogether() throws Exception {
        Exam exam = exam("closed", true);
        ExamRecord record = inProgressRecord();
        record.setStatus("submitted");
        Answer answer = new Answer();
        answer.setId(40L);
        answer.setRecordId(2L);
        answer.setQuestionId(4L);
        answer.setScoreGot(0.0);
        answer.setIsCorrect("N");
        when(examMapper.selectOne(any())).thenReturn(exam);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(4L)).thenReturn(singleChoiceQuestion());
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("grades.xlsx");
        when(multipartFile.getBytes()).thenReturn(new byte[]{1});
        when(excelService.parse(any(), any())).thenReturn(List.of(Map.of(
                "record_id", "2", "student_id", "3",
                "question_4_score", "3", "total_score", "3")));
        when(examRecordMapper.selectById(2L)).thenReturn(record);
        when(examRecordMapper.selectOne(any())).thenReturn(record);
        when(answerMapper.selectList(any())).thenReturn(List.of(answer));

        Map<String, Object> result = controller.importGrades(1L, multipartFile, teacherUser());

        assertEquals(1, result.get("updated"));
        assertEquals(3.0, answer.getScoreGot());
        assertEquals("P", answer.getIsCorrect());
        assertEquals(3.0, record.getTotalScore());
        assertEquals("graded", record.getStatus());
        assertTrue(record.getEssayGraded());
    }

    private Exam exam(String status, boolean scorePublic) {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setTitle("测试考试");
        exam.setStatus(status);
        exam.setScorePublic(scorePublic);
        exam.setTimeLimit(60);
        exam.setTotalScore(5.0);
        exam.setCloseTime(LocalDateTime.now(APP_ZONE).plusHours(1));
        return exam;
    }

    private ExamRecord inProgressRecord() {
        ExamRecord record = new ExamRecord();
        record.setId(2L);
        record.setExamId(1L);
        record.setStudentId(3L);
        record.setStatus("in_progress");
        record.setStartedAt(LocalDateTime.now(APP_ZONE));
        record.setAttemptCount(1);
        record.setEssayGraded(false);
        record.setVersion(1);
        return record;
    }

    private ExamQuestion examQuestion() {
        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setId(5L);
        examQuestion.setExamId(1L);
        examQuestion.setQuestionId(4L);
        examQuestion.setOrderNum(1);
        examQuestion.setScore(5.0);
        return examQuestion;
    }

    private Question singleChoiceQuestion() {
        Question question = new Question();
        question.setId(4L);
        question.setTitle("示例题目");
        question.setQuestionType("single");
        question.setDifficulty("medium");
        question.setOptions("[\"A. 正确\",\"B. 错误\"]");
        question.setAnswer("A");
        return question;
    }

    private ExamSubmitDTO submission(String answer) {
        AnswerItemDTO item = new AnswerItemDTO();
        item.setQuestionId(4L);
        item.setUserAnswer(answer);
        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setRecordId(2L);
        dto.setDraftVersion(1);
        dto.setAnswers(List.of(item));
        return dto;
    }

    private LoginUser loginUser() {
        User user = new User();
        user.setId(3L);
        user.setUsername("student");
        user.setRole("student");
        user.setIsActive(true);
        return new LoginUser(user);
    }

    private LoginUser teacherUser() {
        User user = new User();
        user.setId(8L);
        user.setUsername("admin");
        user.setRole("admin");
        user.setIsActive(true);
        return new LoginUser(user);
    }
}
