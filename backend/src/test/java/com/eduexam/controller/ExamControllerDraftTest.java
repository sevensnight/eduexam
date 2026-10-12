package com.eduexam.controller;

import com.eduexam.domain.Answer;
import com.eduexam.domain.Exam;
import com.eduexam.domain.ExamQuestion;
import com.eduexam.domain.ExamRecord;
import com.eduexam.domain.Question;
import com.eduexam.domain.User;
import com.eduexam.dto.AnswerItemDTO;
import com.eduexam.dto.ExamSubmitDTO;
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
import com.eduexam.service.ExcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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

    private ExamController controller;

    @BeforeEach
    void setUp() {
        controller = new ExamController(examMapper, examQuestionMapper, examRecordMapper,
                answerMapper, questionMapper, questionTagMapper, userMapper, courseMapper,
                enrollmentMapper, notificationMapper, excelService);
    }

    @Test
    void savesDraftWithoutGradingOrRevealingCorrectness() {
        Exam exam = exam("published", true);
        ExamRecord record = inProgressRecord();
        ExamQuestion examQuestion = examQuestion();
        Question question = singleChoiceQuestion();
        when(examMapper.selectOne(any())).thenReturn(exam);
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
        when(examRecordMapper.selectOne(any())).thenReturn(record);
        when(userMapper.selectById(3L)).thenReturn(loginUser().getUser());
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

    private Exam exam(String status, boolean scorePublic) {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setTitle("测试考试");
        exam.setStatus(status);
        exam.setScorePublic(scorePublic);
        exam.setTimeLimit(60);
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
        question.setQuestionType("single");
        question.setAnswer("A");
        return question;
    }

    private ExamSubmitDTO submission(String answer) {
        AnswerItemDTO item = new AnswerItemDTO();
        item.setQuestionId(4L);
        item.setUserAnswer(answer);
        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setRecordId(2L);
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
}
