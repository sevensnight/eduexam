package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.dto.*;
import com.eduexam.mapper.*;
import com.eduexam.security.LoginUser;
import com.eduexam.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamMapper examMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final NotificationMapper notificationMapper;
    private final WrongBookEntryMapper wrongBookMapper;
    private final ExcelService excelService;

    // ======================== Helpers ========================

    private Map<String, Object> examToMap(Exam exam, List<ExamQuestion> examQuestions, boolean includeQuestions) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", exam.getId());
        m.put("title", exam.getTitle());
        m.put("description", exam.getDescription());
        m.put("teacher_id", exam.getTeacherId());
        m.put("course_id", exam.getCourseId());
        m.put("target_class", exam.getTargetClass());
        m.put("status", exam.getStatus());
        m.put("open_time", exam.getOpenTime());
        m.put("close_time", exam.getCloseTime());
        m.put("time_limit", exam.getTimeLimit());
        m.put("duration_minutes", exam.getTimeLimit());
        m.put("max_attempts", exam.getMaxAttempts());
        m.put("allow_retake", exam.getAllowRetake());
        m.put("score_public", exam.getScorePublic());
        m.put("total_score", exam.getTotalScore());
        m.put("pass_score", exam.getPassScore());
        m.put("created_at", exam.getCreatedAt());
        m.put("updated_at", exam.getUpdatedAt());

        String courseName = null;
        if (exam.getCourseId() != null) {
            Course course = courseMapper.selectById(exam.getCourseId());
            if (course != null) courseName = course.getName();
        }
        m.put("course_name", courseName);

        List<ExamQuestion> eqsForStats = examQuestions != null ? examQuestions :
                examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, exam.getId()));
        m.put("question_count", eqsForStats.size());
        boolean hasEssay = false;
        if (!eqsForStats.isEmpty()) {
            Set<Long> qIds = eqsForStats.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
            hasEssay = questionMapper.selectBatchIds(new ArrayList<>(qIds)).stream()
                    .anyMatch(q -> "essay".equals(q.getQuestionType()));
        }
        m.put("has_essay", hasEssay);

        if (includeQuestions && examQuestions != null) {
            List<Map<String, Object>> qList = examQuestions.stream().map(eq -> {
                Question q = questionMapper.selectById(eq.getQuestionId());
                Map<String, Object> qm = new LinkedHashMap<>();
                qm.put("exam_question_id", eq.getId());
                qm.put("question_id", eq.getQuestionId());
                qm.put("order_num", eq.getOrderNum());
                qm.put("score", eq.getScore());
                if (q != null) {
                    qm.put("title", q.getTitle());
                    qm.put("question_title", q.getTitle());
                    qm.put("question_type", q.getQuestionType());
                    qm.put("difficulty", q.getDifficulty());
                    qm.put("options", q.getOptions());
                    qm.put("answer", q.getAnswer());
                    qm.put("explanation", q.getExplanation());
                }
                return qm;
            }).collect(Collectors.toList());
            m.put("questions", qList);
            m.put("exam_questions", qList);
        }
        return m;
    }

    private boolean canAccessExam(Exam exam, LoginUser loginUser) {
        if ("admin".equals(loginUser.getRole())) return true;
        if ("teacher".equals(loginUser.getRole())) return loginUser.getUserId().equals(exam.getTeacherId());
        return false;
    }

    private void persistExamQuestions(Long examId, List<ExamQuestionItemDTO> items) {
        examQuestionMapper.delete(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        if (items == null) return;
        int order = 1;
        for (ExamQuestionItemDTO item : items) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(examId); eq.setQuestionId(item.getQuestionId());
            eq.setOrderNum(item.getOrderNum() != null ? item.getOrderNum() : order);
            eq.setScore(item.getScore() != null ? item.getScore() : 10);
            examQuestionMapper.insert(eq);
            questionMapper.incrementUsageCount(item.getQuestionId());
            order++;
        }
    }

    private double computeTotalScore(Long examId) {
        return examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId))
                .stream().mapToDouble(ExamQuestion::getScore).sum();
    }

    private void sendNotification(Long userId, String content, String type) {
        Notification n = new Notification();
        n.setUserId(userId); n.setContent(content); n.setType(type); n.setIsRead(false);
        notificationMapper.insert(n);
    }

    // ======================== Teacher/Admin CRUD ========================

    @GetMapping
    public List<Map<String, Object>> listExams(@AuthenticationPrincipal LoginUser loginUser) {
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<Exam>()
                .orderByDesc(Exam::getCreatedAt);
        if ("teacher".equals(loginUser.getRole())) wrapper.eq(Exam::getTeacherId, loginUser.getUserId());
        else if ("student".equals(loginUser.getRole())) {
            String studentClass = userMapper.selectById(loginUser.getUserId()).getClassName();
            // published exams targeting student's class (or all classes)
            List<Exam> published = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                    .eq(Exam::getStatus, "published")
                    .and(w -> w.isNull(Exam::getTargetClass).or().eq(studentClass != null, Exam::getTargetClass, studentClass))
                    .orderByDesc(Exam::getCreatedAt));
            // closed exams the student has a record for
            List<Long> participatedIds = examRecordMapper.selectList(
                    new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getStudentId, loginUser.getUserId()))
                    .stream().map(ExamRecord::getExamId).distinct().collect(Collectors.toList());
            List<Exam> closedParticipated = participatedIds.isEmpty() ? List.of() :
                    examMapper.selectList(new LambdaQueryWrapper<Exam>()
                            .eq(Exam::getStatus, "closed")
                            .in(Exam::getId, participatedIds)
                            .orderByDesc(Exam::getCreatedAt));
            Map<Long, Exam> merged = new LinkedHashMap<>();
            published.forEach(e -> merged.put(e.getId(), e));
            closedParticipated.forEach(e -> merged.put(e.getId(), e));
            List<Exam> all = new ArrayList<>(merged.values());
            all.sort(Comparator.comparing(Exam::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return all.stream().map(e -> examToMap(e, null, false)).collect(Collectors.toList());
        }
        List<Exam> exams = examMapper.selectList(wrapper);
        return exams.stream().map(e -> examToMap(e, null, false)).collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> createExam(@RequestBody ExamCreateDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = new Exam();
        applyExamDTO(exam, dto, loginUser);
        examMapper.insert(exam);
        if (dto.getQuestions() != null) persistExamQuestions(exam.getId(), dto.getQuestions());
        exam.setTotalScore(computeTotalScore(exam.getId()));
        examMapper.updateById(exam);
        List<ExamQuestion> eqs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, exam.getId()).orderByAsc(ExamQuestion::getOrderNum));
        return examToMap(exam, eqs, true);
    }

    @GetMapping("/{examId}")
    public Map<String, Object> getExam(@PathVariable Long examId,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("student".equals(loginUser.getRole())) {
            if (!List.of("published", "closed").contains(exam.getStatus()))
                throw AppException.notFound("考试不存在");
        } else if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权访问");
        List<ExamQuestion> eqs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId)
                        .orderByAsc(ExamQuestion::getOrderNum));
        Map<String, Object> result = examToMap(exam, eqs, true);
        if ("student".equals(loginUser.getRole())) {
            // Hide answers for ongoing exams
            if ("published".equals(exam.getStatus())) {
                List<?> questions = (List<?>) result.get("questions");
                if (questions != null) {
                    for (Object q : questions) {
                        if (q instanceof Map<?, ?> qm) {
                            ((Map<String, Object>) qm).remove("answer");
                            ((Map<String, Object>) qm).remove("explanation");
                        }
                    }
                }
            }
        }
        return result;
    }

    @PutMapping("/{examId}")
    public Map<String, Object> updateExam(@PathVariable Long examId, @RequestBody ExamUpdateDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        String oldStatus = exam.getStatus();
        applyExamUpdateDTO(exam, dto, loginUser);
        if (dto.getQuestions() != null) persistExamQuestions(examId, dto.getQuestions());
        exam.setTotalScore(computeTotalScore(examId));
        examMapper.updateById(exam);

        String newStatus = exam.getStatus();
        if (!newStatus.equals(oldStatus)) {
            if ("published".equals(newStatus)) {
                notifyTargetStudents(exam, "考试《" + exam.getTitle() + "》已发布，快去参加吧！", "exam");
            } else if ("closed".equals(newStatus)) {
                notifyParticipants(examId, "考试《" + exam.getTitle() + "》已关闭。", "exam");
            }
        }

        List<ExamQuestion> eqs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId).orderByAsc(ExamQuestion::getOrderNum));
        return examToMap(exam, eqs, true);
    }

    private void notifyTargetStudents(Exam exam, String content, String type) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>().eq(User::getRole, "student").eq(User::getIsActive, true);
        if (exam.getTargetClass() != null && !exam.getTargetClass().isBlank())
            w.eq(User::getClassName, exam.getTargetClass());
        userMapper.selectList(w).forEach(u -> sendNotification(u.getId(), content, type));
    }

    private void notifyParticipants(Long examId, String content, String type) {
        examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId))
                .stream().map(ExamRecord::getStudentId).distinct()
                .forEach(sid -> sendNotification(sid, content, type));
    }

    @DeleteMapping("/{examId}")
    public Map<String, Boolean> deleteExam(@PathVariable Long examId,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        examQuestionMapper.delete(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId));
        for (ExamRecord r : records)
            answerMapper.delete(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, r.getId()));
        examRecordMapper.delete(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId));
        examMapper.deleteById(examId);
        return Map.of("ok", true);
    }

    // ======================== Student Exam Flow ========================

    @PostMapping("/{examId}/start")
    public Map<String, Object> startExam(@PathVariable Long examId,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可参加考试");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!"published".equals(exam.getStatus())) throw AppException.badRequest("考试未开放");
        LocalDateTime now = LocalDateTime.now();
        if (exam.getOpenTime() != null && now.isBefore(exam.getOpenTime()))
            throw AppException.badRequest("考试尚未开始");
        if (exam.getCloseTime() != null && now.isAfter(exam.getCloseTime()))
            throw AppException.badRequest("考试已结束");
        User student = userMapper.selectById(loginUser.getUserId());
        if (exam.getTargetClass() != null && !exam.getTargetClass().isEmpty() &&
                !exam.getTargetClass().equals(student.getClassName()))
            throw AppException.badRequest("您的班级不在此次考试范围内");

        List<ExamRecord> existing = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, loginUser.getUserId()));
        // Check for in-progress attempt
        for (ExamRecord r : existing) {
            if ("in_progress".equals(r.getStatus())) {
                // Return existing in-progress record
                return buildStartResponse(exam, r);
            }
        }
        long completedCount = existing.stream().filter(r -> "submitted".equals(r.getStatus()) || "graded".equals(r.getStatus())).count();
        if (exam.getMaxAttempts() != null && completedCount >= exam.getMaxAttempts())
            throw AppException.badRequest("已达到最大考试次数");
        if (!Boolean.TRUE.equals(exam.getAllowRetake()) && completedCount > 0)
            throw AppException.badRequest("本考试不允许重考");

        ExamRecord record = new ExamRecord();
        record.setExamId(examId); record.setStudentId(loginUser.getUserId());
        record.setStatus("in_progress"); record.setStartedAt(now);
        record.setAttemptCount((int) completedCount + 1); record.setEssayGraded(false);
        examRecordMapper.insert(record);
        return buildStartResponse(exam, record);
    }

    private Map<String, Object> buildStartResponse(Exam exam, ExamRecord record) {
        List<ExamQuestion> eqs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, exam.getId())
                        .orderByAsc(ExamQuestion::getOrderNum));
        List<Map<String, Object>> qList = eqs.stream().map(eq -> {
            Question q = questionMapper.selectById(eq.getQuestionId());
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("exam_question_id", eq.getId());
            qm.put("question_id", eq.getQuestionId());
            qm.put("order_num", eq.getOrderNum());
            qm.put("score", eq.getScore());
            if (q != null) {
                qm.put("title", q.getTitle());
                qm.put("question_title", q.getTitle());
                qm.put("question_type", q.getQuestionType());
                qm.put("difficulty", q.getDifficulty());
                qm.put("options", q.getOptions());
            }
            return qm;
        }).collect(Collectors.toList());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("record_id", record.getId());
        resp.put("id", record.getId());
        resp.put("exam_id", exam.getId());
        resp.put("started_at", record.getStartedAt());
        resp.put("time_limit", exam.getTimeLimit());
        resp.put("duration_minutes", exam.getTimeLimit());
        resp.put("questions", qList);
        resp.put("exam_questions", qList);
        resp.put("attempt_count", record.getAttemptCount());
        return resp;
    }

    @PostMapping("/{examId}/submit")
    public Map<String, Object> submitExam(@PathVariable Long examId, @RequestBody ExamSubmitDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可提交");
        ExamRecord record = examRecordMapper.selectById(dto.getRecordId());
        if (record == null || !record.getExamId().equals(examId) || !record.getStudentId().equals(loginUser.getUserId()))
            throw AppException.notFound("考试记录不存在");
        if (!"in_progress".equals(record.getStatus())) throw AppException.badRequest("考试记录状态不正确");

        Exam exam = examMapper.selectById(examId);
        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        Map<Long, ExamQuestion> qMap = examQuestions.stream().collect(Collectors.toMap(ExamQuestion::getQuestionId, eq -> eq));

        double totalScore = 0;
        boolean hasEssay = false;
        List<Answer> answerList = new ArrayList<>();

        for (AnswerItemDTO item : dto.getAnswers()) {
            ExamQuestion eq = qMap.get(item.getQuestionId());
            if (eq == null) continue;
            Question q = questionMapper.selectById(item.getQuestionId());
            if (q == null) continue;

            Answer ans = new Answer();
            ans.setRecordId(record.getId()); ans.setQuestionId(item.getQuestionId());
            ans.setUserAnswer(item.getUserAnswer());

            String isCorrect; double scoreGot = 0;
            if ("essay".equals(q.getQuestionType())) {
                isCorrect = "P"; hasEssay = true;
            } else {
                isCorrect = judgeAnswer(q, item.getUserAnswer()) ? "Y" : "N";
                if ("Y".equals(isCorrect)) { scoreGot = eq.getScore(); totalScore += scoreGot; }
            }
            ans.setIsCorrect(isCorrect); ans.setScoreGot(scoreGot);
            answerList.add(ans);
        }
        answerMapper.delete(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()));
        for (Answer ans : answerList) answerMapper.insert(ans);

        record.setStatus(hasEssay ? "submitted" : "graded");
        record.setSubmittedAt(LocalDateTime.now());
        record.setTotalScore(totalScore);
        record.setEssayGraded(!hasEssay);
        examRecordMapper.updateById(record);

        // Add wrong book entries for incorrect answers
        for (Answer ans : answerList) {
            if ("N".equals(ans.getIsCorrect())) {
                long exists = wrongBookMapper.selectCount(new LambdaQueryWrapper<WrongBookEntry>()
                        .eq(WrongBookEntry::getStudentId, loginUser.getUserId())
                        .eq(WrongBookEntry::getQuestionId, ans.getQuestionId()));
                if (exists == 0) {
                    WrongBookEntry wb = new WrongBookEntry();
                    wb.setStudentId(loginUser.getUserId()); wb.setQuestionId(ans.getQuestionId());
                    wrongBookMapper.insert(wb);
                }
            }
        }

        if ("graded".equals(record.getStatus())) {
            sendNotification(loginUser.getUserId(),
                    "您的考试《" + exam.getTitle() + "》已完成评分，得分：" + totalScore, "grade");
        }

        return Map.of("record_id", record.getId(), "status", record.getStatus(),
                "total_score", totalScore, "essay_graded", record.getEssayGraded(),
                "submitted_at", record.getSubmittedAt());
    }

    private boolean judgeAnswer(Question q, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String correct = q.getAnswer();
        if ("single".equals(q.getQuestionType())) return userAnswer.trim().equalsIgnoreCase(correct.trim());
        if ("truefalse".equals(q.getQuestionType())) return userAnswer.trim().equalsIgnoreCase(correct.trim());
        if ("multiple".equals(q.getQuestionType())) {
            String[] userParts = userAnswer.split(","); String[] correctParts = correct.split(",");
            Set<String> userSet = Arrays.stream(userParts).map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
            Set<String> correctSet = Arrays.stream(correctParts).map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
            return userSet.equals(correctSet);
        }
        return false;
    }

    // ======================== Records & Grading ========================

    @GetMapping("/{examId}/records")
    public List<Map<String, Object>> listRecords(@PathVariable Long examId,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId).orderByDesc(ExamRecord::getSubmittedAt);
        if ("student".equals(loginUser.getRole())) {
            wrapper.eq(ExamRecord::getStudentId, loginUser.getUserId());
        } else if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权访问");

        List<ExamRecord> records = examRecordMapper.selectList(wrapper);
        Set<Long> studentIds = records.stream().map(ExamRecord::getStudentId).collect(Collectors.toSet());
        Map<Long, User> studentMap = studentIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(new ArrayList<>(studentIds)).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // Load exam questions for answer sheet
        List<ExamQuestion> examQs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId)
                        .orderByAsc(ExamQuestion::getOrderNum));
        Set<Long> questionIds = examQs.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Map.of() :
                questionMapper.selectBatchIds(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));

        // Batch load all answers for all records
        List<Long> recordIds = records.stream().map(ExamRecord::getId).collect(Collectors.toList());
        Map<Long, List<Answer>> answersByRecord = recordIds.isEmpty() ? Map.of() :
                answerMapper.selectList(new LambdaQueryWrapper<Answer>().in(Answer::getRecordId, recordIds))
                        .stream().collect(Collectors.groupingBy(Answer::getRecordId));

        return records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("exam_id", r.getExamId());
            m.put("student_id", r.getStudentId());
            User s = studentMap.get(r.getStudentId());
            m.put("student_name", s != null ? (s.getRealName() != null ? s.getRealName() : s.getUsername()) : null);
            m.put("student_username", s != null ? s.getUsername() : null);
            m.put("class_name", s != null ? s.getClassName() : null);
            m.put("status", r.getStatus()); m.put("started_at", r.getStartedAt());
            m.put("submitted_at", r.getSubmittedAt()); m.put("total_score", r.getTotalScore());
            m.put("attempt_count", r.getAttemptCount()); m.put("essay_graded", r.getEssayGraded());

            Map<Long, Answer> answerByQid = answersByRecord.getOrDefault(r.getId(), List.of())
                    .stream().collect(Collectors.toMap(Answer::getQuestionId, a -> a, (a, b) -> a));
            List<Map<String, Object>> answers = examQs.stream().map(eq -> {
                Question q = questionMap.get(eq.getQuestionId());
                Answer ans = answerByQid.get(eq.getQuestionId());
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("id", ans != null ? ans.getId() : null);
                am.put("question_id", eq.getQuestionId());
                am.put("question_title", q != null ? q.getTitle() : null);
                am.put("question_type", q != null ? q.getQuestionType() : null);
                am.put("user_answer", ans != null ? ans.getUserAnswer() : null);
                am.put("correct_answer", q != null ? q.getAnswer() : null);
                am.put("is_correct", ans != null ? ans.getIsCorrect() : null);
                am.put("score_got", ans != null ? ans.getScoreGot() : 0);
                am.put("max_score", eq.getScore());
                return am;
            }).collect(Collectors.toList());
            m.put("answers", answers);
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/{examId}/my-record")
    public Map<String, Object> myRecord(@PathVariable Long examId,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, loginUser.getUserId())
                        .orderByDesc(ExamRecord::getStartedAt));
        if (records.isEmpty()) return Map.of("exists", false);
        ExamRecord record = records.stream()
                .filter(r -> !"in_progress".equals(r.getStatus()))
                .findFirst().orElse(records.get(0));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exists", true);
        m.put("id", record.getId());
        m.put("exam_id", record.getExamId());
        m.put("status", record.getStatus());
        m.put("total_score", record.getTotalScore());
        m.put("started_at", record.getStartedAt());
        m.put("submitted_at", record.getSubmittedAt());
        m.put("attempt_count", record.getAttemptCount());
        m.put("essay_graded", record.getEssayGraded());

        List<ExamQuestion> examQs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId).orderByAsc(ExamQuestion::getOrderNum));
        Set<Long> questionIds = examQs.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Map.of() :
                questionMapper.selectBatchIds(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));
        List<Answer> rawAnswers = answerMapper.selectList(
                new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()));
        Map<Long, Answer> answerByQid = rawAnswers.stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> a, (a, b) -> a));
        List<Map<String, Object>> answers = examQs.stream().map(eq -> {
            Question q = questionMap.get(eq.getQuestionId());
            Answer ans = answerByQid.get(eq.getQuestionId());
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", ans != null ? ans.getId() : null);
            am.put("question_id", eq.getQuestionId());
            am.put("question_title", q != null ? q.getTitle() : null);
            am.put("question_type", q != null ? q.getQuestionType() : null);
            am.put("user_answer", ans != null ? ans.getUserAnswer() : null);
            am.put("correct_answer", q != null ? q.getAnswer() : null);
            am.put("is_correct", ans != null ? ans.getIsCorrect() : null);
            am.put("score_got", ans != null ? ans.getScoreGot() : 0);
            am.put("max_score", eq.getScore());
            return am;
        }).collect(Collectors.toList());
        m.put("answers", answers);
        return m;
    }

    @GetMapping("/{examId}/records/{recordId}")
    public Map<String, Object> getRecord(@PathVariable Long examId, @PathVariable Long recordId,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null || !record.getExamId().equals(examId)) throw AppException.notFound("考试记录不存在");
        if ("student".equals(loginUser.getRole()) && !record.getStudentId().equals(loginUser.getUserId()))
            throw AppException.forbidden("无权访问");
        else if (!canAccessExam(exam, loginUser) && !"student".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");

        List<Answer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, recordId));
        List<ExamQuestion> examQs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId).orderByAsc(ExamQuestion::getOrderNum));
        Map<Long, ExamQuestion> eqMap = examQs.stream().collect(Collectors.toMap(ExamQuestion::getQuestionId, eq -> eq));
        Map<Long, Answer> answerMap = answers.stream().collect(Collectors.toMap(Answer::getQuestionId, a -> a));

        boolean showAnswer = !"student".equals(loginUser.getRole()) ||
                "graded".equals(record.getStatus()) ||
                "closed".equals(exam.getStatus());

        List<Map<String, Object>> qDetails = examQs.stream().map(eq -> {
            Question q = questionMapper.selectById(eq.getQuestionId());
            Answer ans = answerMap.get(eq.getQuestionId());
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("exam_question_id", eq.getId()); qm.put("question_id", eq.getQuestionId());
            qm.put("order_num", eq.getOrderNum()); qm.put("score", eq.getScore());
            if (q != null) {
                qm.put("title", q.getTitle()); qm.put("question_type", q.getQuestionType());
                qm.put("difficulty", q.getDifficulty()); qm.put("options", q.getOptions());
                if (showAnswer) { qm.put("answer", q.getAnswer()); qm.put("explanation", q.getExplanation()); }
            }
            if (ans != null) {
                qm.put("answer_id", ans.getId()); qm.put("user_answer", ans.getUserAnswer());
                qm.put("is_correct", ans.getIsCorrect()); qm.put("score_got", ans.getScoreGot());
            } else { qm.put("user_answer", null); qm.put("is_correct", null); qm.put("score_got", 0); }
            return qm;
        }).collect(Collectors.toList());

        User student = userMapper.selectById(record.getStudentId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId()); result.put("exam_id", record.getExamId());
        result.put("student_id", record.getStudentId());
        result.put("student_name", student != null ? (student.getRealName() != null ? student.getRealName() : student.getUsername()) : null);
        result.put("class_name", student != null ? student.getClassName() : null);
        result.put("status", record.getStatus()); result.put("started_at", record.getStartedAt());
        result.put("submitted_at", record.getSubmittedAt()); result.put("total_score", record.getTotalScore());
        result.put("attempt_count", record.getAttemptCount()); result.put("essay_graded", record.getEssayGraded());
        result.put("questions", qDetails);
        return result;
    }

    @PutMapping("/{examId}/records/{recordId}/grade")
    public Map<String, Object> gradeEssay(@PathVariable Long examId, @PathVariable Long recordId,
                                           @RequestBody EssayGradeDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null || !record.getExamId().equals(examId)) throw AppException.notFound("考试记录不存在");
        if (!"submitted".equals(record.getStatus())) throw AppException.badRequest("该记录无需批改");

        double addedScore = 0;
        for (EssayGradeDTO.EssayGradeItemDTO item : dto.getGrades()) {
            Answer ans = answerMapper.selectById(item.getAnswerId());
            if (ans == null || !ans.getRecordId().equals(recordId)) continue;
            ans.setIsCorrect(item.getIsCorrect()); ans.setScoreGot(item.getScoreGot());
            answerMapper.updateById(ans);
            addedScore += item.getScoreGot();
        }
        double objScore = answerMapper.selectList(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, recordId))
                .stream().filter(a -> !"P".equals(a.getIsCorrect())).mapToDouble(Answer::getScoreGot).sum();
        record.setTotalScore(objScore + addedScore - dto.getGrades().stream().mapToDouble(EssayGradeDTO.EssayGradeItemDTO::getScoreGot).sum() + addedScore);
        // Recalculate properly
        double totalScoreCalc = answerMapper.selectList(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, recordId))
                .stream().mapToDouble(a -> a.getScoreGot() != null ? a.getScoreGot() : 0).sum();
        record.setTotalScore(totalScoreCalc);
        record.setStatus("graded"); record.setEssayGraded(true);
        examRecordMapper.updateById(record);
        sendNotification(record.getStudentId(),
                "您的考试《" + exam.getTitle() + "》已批改完成，得分：" + totalScoreCalc, "grade");
        return Map.of("ok", true, "total_score", totalScoreCalc, "essay_graded", true);
    }

    @GetMapping("/{examId}/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathVariable Long examId,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("student".equals(loginUser.getRole())) {
            if (!Boolean.TRUE.equals(exam.getScorePublic())) throw AppException.forbidden("成绩未公开");
        } else if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权访问");

        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                        .in(ExamRecord::getStatus, "graded", "submitted")
                        .orderByDesc(ExamRecord::getTotalScore).orderByAsc(ExamRecord::getSubmittedAt));
        Set<Long> studentIds = records.stream().map(ExamRecord::getStudentId).collect(Collectors.toSet());
        Map<Long, User> studentMap = studentIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        int rank = 1;
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExamRecord r : records) {
            User s = studentMap.get(r.getStudentId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++); m.put("record_id", r.getId());
            m.put("student_id", r.getStudentId());
            m.put("student_name", s != null ? (s.getRealName() != null ? s.getRealName() : s.getUsername()) : null);
            m.put("username", s != null ? s.getUsername() : null);
            m.put("real_name", s != null ? s.getRealName() : null);
            m.put("class_name", s != null ? s.getClassName() : null);
            m.put("total_score", r.getTotalScore()); m.put("submitted_at", r.getSubmittedAt());
            m.put("attempt_count", r.getAttemptCount());
            result.add(m);
        }
        return result;
    }

    @GetMapping("/{examId}/grades/export")
    public ResponseEntity<byte[]> exportGrades(@PathVariable Long examId,
                                                @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                        .in(ExamRecord::getStatus, "graded", "submitted").orderByAsc(ExamRecord::getStudentId));
        Set<Long> sids = records.stream().map(ExamRecord::getStudentId).collect(Collectors.toSet());
        Map<Long, User> sm = sids.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(sids).stream().collect(Collectors.toMap(User::getId, u -> u));
        List<String> headers = List.of("student_id", "username", "real_name", "class_name",
                "total_score", "status", "submitted_at", "attempt_count");
        List<Map<String, Object>> rows = records.stream().map(r -> {
            User s = sm.get(r.getStudentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_id", r.getStudentId());
            row.put("username", s != null ? s.getUsername() : ""); row.put("real_name", s != null ? s.getRealName() : "");
            row.put("class_name", s != null ? s.getClassName() : "");
            row.put("total_score", r.getTotalScore()); row.put("status", r.getStatus());
            row.put("submitted_at", r.getSubmittedAt() != null ? r.getSubmittedAt().toString() : "");
            row.put("attempt_count", r.getAttemptCount());
            return row;
        }).collect(Collectors.toList());
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        byte[] bytes = excelService.export(headers, rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"grades_" + examId + "_" + ts + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/{examId}/grades/import")
    public Map<String, Object> importGrades(@PathVariable Long examId,
                                             @RequestParam("file") MultipartFile file,
                                             @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        List<Map<String, String>> rows = excelService.parse(file.getOriginalFilename(), file.getBytes());
        int updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNum = i + 2;
            try {
                String sidStr = row.getOrDefault("student_id", "").trim();
                if (sidStr.isEmpty()) continue;
                Long sid = Long.parseLong(sidStr);
                String scoreStr = row.getOrDefault("total_score", "").trim();
                if (scoreStr.isEmpty()) continue;
                double score = Double.parseDouble(scoreStr);
                List<ExamRecord> recs = examRecordMapper.selectList(
                        new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                                .eq(ExamRecord::getStudentId, sid)
                                .in(ExamRecord::getStatus, "graded", "submitted")
                                .orderByDesc(ExamRecord::getSubmittedAt).last("LIMIT 1"));
                if (recs.isEmpty()) throw new IllegalArgumentException("找不到该学生的考试记录");
                ExamRecord r = recs.get(0);
                r.setTotalScore(score); r.setStatus("graded"); r.setEssayGraded(true);
                examRecordMapper.updateById(r);
                sendNotification(sid, "您的考试《" + exam.getTitle() + "》成绩已更新：" + score, "grade");
                updated++;
            } catch (NumberFormatException ex) {
                errors.add(Map.of("row", lineNum, "message", "数字格式错误: " + ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                errors.add(Map.of("row", lineNum, "message", ex.getMessage()));
            }
        }
        return Map.of("created", rows.size(), "updated", updated, "errors", errors);
    }

    private void applyExamDTO(Exam exam, ExamCreateDTO dto, LoginUser loginUser) {
        exam.setTitle(dto.getTitle()); exam.setDescription(dto.getDescription());
        exam.setTeacherId("admin".equals(loginUser.getRole()) && dto.getTeacherId() != null ?
                dto.getTeacherId() : loginUser.getUserId());
        exam.setCourseId(dto.getCourseId()); exam.setTargetClass(dto.getTargetClass());
        exam.setStatus(dto.getStatus() != null ? dto.getStatus() : "draft");
        exam.setOpenTime(dto.getOpenTime()); exam.setCloseTime(dto.getCloseTime());
        exam.setTimeLimit(dto.getTimeLimit()); exam.setMaxAttempts(dto.getMaxAttempts());
        exam.setAllowRetake(dto.getAllowRetake() != null ? dto.getAllowRetake() : false);
        exam.setScorePublic(dto.getScorePublic() != null ? dto.getScorePublic() : true);
        exam.setPassScore(dto.getPassScore()); exam.setTotalScore(0.0);
    }

    private void applyExamUpdateDTO(Exam exam, ExamUpdateDTO dto, LoginUser loginUser) {
        if (dto.getTitle() != null) exam.setTitle(dto.getTitle());
        if (dto.getDescription() != null) exam.setDescription(dto.getDescription());
        if ("admin".equals(loginUser.getRole()) && dto.getTeacherId() != null) exam.setTeacherId(dto.getTeacherId());
        if (dto.getCourseId() != null) exam.setCourseId(dto.getCourseId());
        if (dto.getTargetClass() != null) exam.setTargetClass(dto.getTargetClass());
        if (dto.getStatus() != null) exam.setStatus(dto.getStatus());
        if (dto.getOpenTime() != null) exam.setOpenTime(dto.getOpenTime());
        if (dto.getCloseTime() != null) exam.setCloseTime(dto.getCloseTime());
        if (dto.getTimeLimit() != null) exam.setTimeLimit(dto.getTimeLimit());
        if (dto.getMaxAttempts() != null) exam.setMaxAttempts(dto.getMaxAttempts());
        if (dto.getAllowRetake() != null) exam.setAllowRetake(dto.getAllowRetake());
        if (dto.getScorePublic() != null) exam.setScorePublic(dto.getScorePublic());
        if (dto.getPassScore() != null) exam.setPassScore(dto.getPassScore());
    }
}
