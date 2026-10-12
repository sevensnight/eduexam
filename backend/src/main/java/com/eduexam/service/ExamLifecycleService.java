package com.eduexam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Owns state transitions that must behave identically whether initiated by an
 * HTTP request or by the scheduler.  Callers that already hold the exam row
 * lock may use the *Locked methods; all mutations still participate in the
 * caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class ExamLifecycleService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> QUESTION_TYPES = Set.of("single", "multiple", "truefalse", "essay");
    private static final Set<String> DIFFICULTY_LEVELS = Set.of("easy", "medium", "hard");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExamMapper examMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final NotificationMapper notificationMapper;

    @Transactional
    public boolean publishExam(Long examId) {
        Exam exam = lockExam(examId);
        if (exam == null || !"draft".equals(exam.getStatus())) return false;
        publishLocked(exam);
        return true;
    }

    public void publishLocked(Exam exam) {
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!"draft".equals(exam.getStatus())) {
            if ("published".equals(exam.getStatus())) return;
            throw AppException.badRequest("只有草稿考试可以发布");
        }
        List<ExamQuestion> questions = validatePublishable(exam);
        exam.setTotalScore(questions.stream()
                .mapToDouble(item -> item.getScore() != null ? item.getScore() : 0).sum());
        if (exam.getPassScore() != null && exam.getPassScore() > exam.getTotalScore())
            throw AppException.badRequest("及格分不能高于考试总分");
        exam.setStatus("published");
        examMapper.updateById(exam);
        notifyEligibleStudents(exam, "考试《" + exam.getTitle() + "》已发布，快去参加吧！", "exam");
    }

    @Transactional
    public boolean closeExam(Long examId) {
        Exam exam = lockExam(examId);
        if (exam == null) return false;
        boolean changed = !"closed".equals(exam.getStatus());
        closeLocked(exam);
        return changed;
    }

    public void closeLocked(Exam exam) {
        if (exam == null) throw AppException.notFound("考试不存在");
        boolean statusChanged = !"closed".equals(exam.getStatus());

        // Lock every unfinished attempt before changing the exam status.  This
        // serializes closure with autosave/submission and guarantees that the
        // final score comes from the last committed server draft.
        List<ExamRecord> unfinished = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, exam.getId())
                        .eq(ExamRecord::getStatus, "in_progress")
                        .orderByAsc(ExamRecord::getId)
                        .last("FOR UPDATE"));
        LocalDateTime submittedAt = LocalDateTime.now(APP_ZONE);
        for (ExamRecord record : unfinished) finalizeRecord(exam, record, submittedAt);

        if (statusChanged) {
            exam.setStatus("closed");
            examMapper.updateById(exam);
            notifyParticipants(exam.getId(), "考试《" + exam.getTitle() + "》已关闭。", "exam");
        }
    }

    /** Finalize one locked in-progress record from its last persisted draft. */
    public void finalizeRecord(Exam exam, ExamRecord record, LocalDateTime submittedAt) {
        if (record == null || !"in_progress".equals(record.getStatus())) return;
        // Existing published exams must still be closable even if they were
        // created by an older release with malformed choice metadata.
        List<ExamQuestion> questions = validatedQuestions(exam.getId(), false);
        Map<Long, String> savedDraft = answerMapper.selectList(
                        new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()))
                .stream().collect(Collectors.toMap(Answer::getQuestionId,
                        answer -> answer.getUserAnswer() != null ? answer.getUserAnswer() : "",
                        (first, ignored) -> first));

        answerMapper.delete(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()));
        boolean hasEssay = false;
        double totalScore = 0;
        for (ExamQuestion eq : questions) {
            Question question = questionMapper.selectById(eq.getQuestionId());
            if (question == null) throw AppException.badRequest("考试包含不存在的题目");
            String userAnswer = savedDraft.getOrDefault(eq.getQuestionId(), "");
            Answer answer = new Answer();
            answer.setRecordId(record.getId());
            answer.setQuestionId(eq.getQuestionId());
            answer.setUserAnswer(userAnswer);
            if ("essay".equals(question.getQuestionType())) {
                answer.setIsCorrect("P");
                answer.setScoreGot(0.0);
                hasEssay = true;
            } else {
                boolean correct = judgeAnswer(question, userAnswer);
                double score = correct ? eq.getScore() : 0.0;
                answer.setIsCorrect(correct ? "Y" : "N");
                answer.setScoreGot(score);
                totalScore += score;
            }
            answerMapper.insert(answer);
        }
        record.setStatus(hasEssay ? "submitted" : "graded");
        record.setSubmittedAt(submittedAt != null ? submittedAt : LocalDateTime.now(APP_ZONE));
        record.setTotalScore(totalScore);
        record.setEssayGraded(!hasEssay);
        examRecordMapper.updateById(record);
    }

    public List<ExamQuestion> validatePublishable(Exam exam) {
        if (exam.getTitle() == null || exam.getTitle().isBlank())
            throw AppException.badRequest("考试标题不能为空");
        if (exam.getTimeLimit() == null || exam.getTimeLimit() <= 0)
            throw AppException.badRequest("发布考试必须设置有效的考试时长");
        if (exam.getMaxAttempts() != null && exam.getMaxAttempts() <= 0)
            throw AppException.badRequest("最大考试次数必须大于 0");
        if (exam.getOpenTime() != null && exam.getCloseTime() != null
                && !exam.getCloseTime().isAfter(exam.getOpenTime()))
            throw AppException.badRequest("关闭时间必须晚于开放时间");
        if (exam.getCloseTime() != null && !exam.getCloseTime().isAfter(LocalDateTime.now(APP_ZONE)))
            throw AppException.badRequest("考试关闭时间必须晚于当前时间");
        return validatedQuestions(exam.getId(), true);
    }

    private List<ExamQuestion> validatedQuestions(Long examId, boolean strictContent) {
        List<ExamQuestion> questions = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId)
                        .orderByAsc(ExamQuestion::getOrderNum));
        if (questions.isEmpty()) throw AppException.badRequest("考试至少需要配置一道题目");
        Set<Long> seen = new HashSet<>();
        for (ExamQuestion eq : questions) {
            if (eq.getQuestionId() == null || !seen.add(eq.getQuestionId()))
                throw AppException.badRequest("考试题目配置无效");
            if (eq.getScore() == null || !Double.isFinite(eq.getScore()) || eq.getScore() <= 0)
                throw AppException.badRequest("考试题目分值无效");
            Question question = questionMapper.selectById(eq.getQuestionId());
            if (question == null || !QUESTION_TYPES.contains(question.getQuestionType())
                    || question.getAnswer() == null || question.getAnswer().isBlank())
                throw AppException.badRequest("考试包含不存在或内容无效的题目");
            if (strictContent) validateQuestionContent(question);
        }
        return questions;
    }

    private void validateQuestionContent(Question question) {
        if (question.getTitle() == null || question.getTitle().isBlank()
                || !DIFFICULTY_LEVELS.contains(question.getDifficulty()))
            throw AppException.badRequest("考试包含内容无效的题目");
        String type = question.getQuestionType();
        String answer = question.getAnswer().strip();
        if (Set.of("single", "multiple").contains(type)) {
            final List<String> options;
            try {
                options = OBJECT_MAPPER.readValue(question.getOptions(), new TypeReference<List<String>>() { });
            } catch (JsonProcessingException | IllegalArgumentException ex) {
                throw AppException.badRequest("考试选择题的选项格式无效");
            }
            if (options == null || options.size() < 2 || options.size() > 26
                    || options.stream().anyMatch(value -> value == null || value.isBlank()))
                throw AppException.badRequest("考试选择题的选项数量或内容无效");
            Set<String> uniqueOptions = options.stream()
                    .map(value -> value.strip().replaceFirst("^[A-Za-z]\\.\\s*", "").toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            if (uniqueOptions.size() != options.size())
                throw AppException.badRequest("考试选择题包含重复选项");
            List<String> choices = Arrays.stream(answer.toUpperCase(Locale.ROOT).split(","))
                    .map(String::strip).filter(value -> !value.isEmpty()).toList();
            if (choices.isEmpty() || ("single".equals(type) && choices.size() != 1)
                    || new HashSet<>(choices).size() != choices.size())
                throw AppException.badRequest("考试选择题答案无效");
            for (String choice : choices) {
                if (!choice.matches("^[A-Z]$") || choice.charAt(0) - 'A' >= options.size())
                    throw AppException.badRequest("考试选择题答案引用了不存在的选项");
            }
        } else if ("truefalse".equals(type)
                && !Set.of("true", "false").contains(answer.toLowerCase(Locale.ROOT))) {
            throw AppException.badRequest("考试判断题答案必须是 True 或 False");
        }
    }

    private void notifyEligibleStudents(Exam exam, String content, String type) {
        LambdaQueryWrapper<User> usersQuery = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student").eq(User::getIsActive, true);
        if (exam.getTargetClass() != null && !exam.getTargetClass().isBlank())
            usersQuery.apply("TRIM(class_name) = {0}", exam.getTargetClass().strip());
        List<User> candidates = userMapper.selectList(usersQuery);
        Set<Long> enrolled = Set.of();
        if (exam.getCourseId() != null) {
            enrolled = enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getCourseId, exam.getCourseId()))
                    .stream().map(CourseEnrollment::getStudentId).collect(Collectors.toSet());
        }
        for (User student : candidates) {
            if (exam.getCourseId() == null || enrolled.contains(student.getId()))
                sendNotification(student.getId(), content, type);
        }
    }

    private void notifyParticipants(Long examId, String content, String type) {
        examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId))
                .stream().map(ExamRecord::getStudentId).filter(Objects::nonNull).distinct()
                .forEach(studentId -> sendNotification(studentId, content, type));
    }

    private void sendNotification(Long userId, String content, String type) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setContent(content);
        notification.setType(type);
        notification.setIsRead(false);
        notificationMapper.insert(notification);
    }

    private Exam lockExam(Long examId) {
        return examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getId, examId).last("FOR UPDATE"));
    }

    private boolean judgeAnswer(Question question, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()
                || question.getAnswer() == null || question.getAnswer().isBlank()) return false;
        if ("multiple".equals(question.getQuestionType())) {
            Set<String> submitted = Arrays.stream(userAnswer.split(","))
                    .map(String::trim).map(String::toUpperCase).filter(value -> !value.isBlank())
                    .collect(Collectors.toSet());
            Set<String> expected = Arrays.stream(question.getAnswer().split(","))
                    .map(String::trim).map(String::toUpperCase).filter(value -> !value.isBlank())
                    .collect(Collectors.toSet());
            return submitted.equals(expected);
        }
        return userAnswer.trim().equalsIgnoreCase(question.getAnswer().trim());
    }
}
