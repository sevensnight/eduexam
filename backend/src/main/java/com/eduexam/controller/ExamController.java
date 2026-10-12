package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.dto.*;
import com.eduexam.mapper.*;
import com.eduexam.security.LoginUser;
import com.eduexam.service.ExcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> EXAM_STATUSES = Set.of("draft", "published", "closed");
    private static final Set<String> QUESTION_TYPES = Set.of("single", "multiple", "truefalse", "essay");

    private final ExamMapper examMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final NotificationMapper notificationMapper;
    private final ExcelService excelService;

    // ======================== Helpers ========================

    private LocalDateTime now() {
        return LocalDateTime.now(APP_ZONE);
    }

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

    private boolean canStudentAccessExam(Exam exam, Long studentId) {
        User student = userMapper.selectById(studentId);
        if (student == null || !Boolean.TRUE.equals(student.getIsActive())) return false;
        if (exam.getTargetClass() != null && !exam.getTargetClass().isBlank()
                && !exam.getTargetClass().equals(student.getClassName())) return false;
        if (exam.getCourseId() != null && enrollmentMapper.selectCount(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, exam.getCourseId())
                        .eq(CourseEnrollment::getStudentId, studentId)) == 0) return false;
        return true;
    }

    private boolean canStudentSeeAnswerKey(Exam exam, ExamRecord record) {
        if (!Boolean.TRUE.equals(exam.getScorePublic())) return false;
        if (!"graded".equals(record.getStatus())) return false;
        if ("closed".equals(exam.getStatus())) return true;
        return exam.getCloseTime() != null && !now().isBefore(exam.getCloseTime());
    }

    private boolean canStudentSeeResult(Exam exam, ExamRecord record) {
        return canStudentSeeAnswerKey(exam, record);
    }

    private Exam lockExam(Long examId) {
        return examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getId, examId).last("FOR UPDATE"));
    }

    private Exam shareLockExam(Long examId) {
        return examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getId, examId).last("FOR SHARE"));
    }

    private List<ExamQuestion> loadValidExamQuestions(Long examId) {
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
            Question q = questionMapper.selectById(eq.getQuestionId());
            if (q == null || !QUESTION_TYPES.contains(q.getQuestionType()))
                throw AppException.badRequest("考试包含不存在或类型无效的题目");
        }
        return questions;
    }

    private void validateExamConfig(Exam exam, boolean requireQuestions) {
        if (exam.getStatus() == null || !EXAM_STATUSES.contains(exam.getStatus()))
            throw AppException.badRequest("考试状态无效");
        if (exam.getTimeLimit() != null && exam.getTimeLimit() <= 0)
            throw AppException.badRequest("考试时长必须大于 0");
        if (List.of("published", "closed").contains(exam.getStatus()) && exam.getTimeLimit() == null)
            throw AppException.badRequest("发布考试必须设置有效的考试时长");
        if (exam.getMaxAttempts() != null && exam.getMaxAttempts() <= 0)
            throw AppException.badRequest("最大考试次数必须大于 0");
        if (exam.getOpenTime() != null && exam.getCloseTime() != null
                && !exam.getCloseTime().isAfter(exam.getOpenTime()))
            throw AppException.badRequest("关闭时间必须晚于开放时间");
        if (exam.getTotalScore() == null || !Double.isFinite(exam.getTotalScore()) || exam.getTotalScore() < 0)
            throw AppException.badRequest("考试总分无效");
        if (exam.getPassScore() != null
                && (!Double.isFinite(exam.getPassScore()) || exam.getPassScore() < 0
                || exam.getPassScore() > exam.getTotalScore()))
            throw AppException.badRequest("及格分必须在 0 到考试总分之间");
        if (requireQuestions || "published".equals(exam.getStatus())) loadValidExamQuestions(exam.getId());
    }

    private void validateCourseAccess(Long courseId, LoginUser loginUser) {
        if (courseId == null) return;
        Course course = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getId, courseId).last("FOR SHARE"));
        if (course == null) throw AppException.badRequest("课程不存在");
        if ("teacher".equals(loginUser.getRole())
                && !loginUser.getUserId().equals(course.getTeacherId()))
            throw AppException.forbidden("只能使用自己负责的课程");
    }

    private void validateTeacherAssignment(Long teacherId) {
        if (teacherId == null) return;
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null || !"teacher".equals(teacher.getRole()))
            throw AppException.badRequest("授课教师不存在");
    }

    private void persistExamQuestions(Long examId, List<ExamQuestionItemDTO> items, LoginUser loginUser) {
        if (items == null) return;
        Set<Long> questionIds = new HashSet<>();
        for (ExamQuestionItemDTO item : items) {
            if (item == null || item.getQuestionId() == null)
                throw AppException.badRequest("试题编号不能为空");
            if (!questionIds.add(item.getQuestionId()))
                throw AppException.badRequest("同一道题不能重复加入考试");
        }

        // Lock in a stable order. This makes "referenced questions cannot be
        // edited" linearizable and avoids deadlocks when two exams reuse the
        // same group of questions in different display orders.
        Map<Long, Question> lockedQuestions = new HashMap<>();
        questionIds.stream().sorted().forEach(questionId -> {
            Question question = questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                    .eq(Question::getId, questionId).last("FOR UPDATE"));
            if (question == null) throw AppException.badRequest("试题不存在：" + questionId);
            lockedQuestions.put(questionId, question);
        });

        int order = 1;
        List<ExamQuestion> newQuestions = new ArrayList<>();
        for (ExamQuestionItemDTO item : items) {
            Question question = lockedQuestions.get(item.getQuestionId());
            if (!QUESTION_TYPES.contains(question.getQuestionType())
                    || question.getAnswer() == null || question.getAnswer().isBlank())
                throw AppException.badRequest("试题内容或参考答案无效");
            if ("teacher".equals(loginUser.getRole())
                    && !loginUser.getUserId().equals(question.getCreatorId()))
                throw AppException.forbidden("只能使用自己创建的试题");
            double score = item.getScore() != null ? item.getScore() : 10.0;
            if (!Double.isFinite(score) || score <= 0)
                throw AppException.badRequest("每道题的分值必须大于 0");
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(examId); eq.setQuestionId(item.getQuestionId());
            eq.setOrderNum(item.getOrderNum() != null ? item.getOrderNum() : order);
            eq.setScore(score);
            newQuestions.add(eq);
            order++;
        }
        examQuestionMapper.delete(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        for (ExamQuestion eq : newQuestions) {
            examQuestionMapper.insert(eq);
            questionMapper.incrementUsageCount(eq.getQuestionId());
        }
    }

    private double computeTotalScore(Long examId) {
        return examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId))
                .stream().mapToDouble(eq -> eq.getScore() != null ? eq.getScore() : 0).sum();
    }

    private boolean examQuestionsChanged(Long examId, List<ExamQuestionItemDTO> items) {
        if (items == null) return false;
        List<ExamQuestion> existing = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId)
                        .orderByAsc(ExamQuestion::getOrderNum));
        if (existing.size() != items.size()) return true;
        for (int i = 0; i < items.size(); i++) {
            ExamQuestionItemDTO item = items.get(i);
            ExamQuestion current = existing.get(i);
            if (item == null || item.getQuestionId() == null) return true;
            int requestedOrder = item.getOrderNum() != null ? item.getOrderNum() : i + 1;
            double requestedScore = item.getScore() != null ? item.getScore() : 10.0;
            if (!Objects.equals(item.getQuestionId(), current.getQuestionId())
                    || !Objects.equals(requestedOrder, current.getOrderNum())
                    || current.getScore() == null
                    || Double.compare(requestedScore, current.getScore()) != 0)
                return true;
        }
        return false;
    }

    private void ensureExamRulesMutable(Exam exam, ExamUpdateDTO dto, boolean questionsChanged) {
        if (examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, exam.getId())) == 0) return;
        boolean changed = questionsChanged
                || (dto.getTeacherId() != null && !Objects.equals(dto.getTeacherId(), exam.getTeacherId()))
                || (dto.isCourseIdPresent() && !Objects.equals(dto.getCourseId(), exam.getCourseId()))
                || (dto.isTargetClassPresent() && !Objects.equals(dto.getTargetClass(), exam.getTargetClass()))
                || (dto.isOpenTimePresent() && !Objects.equals(dto.getOpenTime(), exam.getOpenTime()))
                || (dto.isCloseTimePresent() && !Objects.equals(dto.getCloseTime(), exam.getCloseTime()))
                || (dto.getTimeLimit() != null && !Objects.equals(dto.getTimeLimit(), exam.getTimeLimit()))
                || (dto.getMaxAttempts() != null && !Objects.equals(dto.getMaxAttempts(), exam.getMaxAttempts()))
                || (dto.getAllowRetake() != null && !Objects.equals(dto.getAllowRetake(), exam.getAllowRetake()))
                || (dto.getPassScore() != null && !Objects.equals(dto.getPassScore(), exam.getPassScore()));
        if (changed)
            throw AppException.badRequest("已有答卷，不能再修改考试题目或规则");
        if (dto.getStatus() != null && !Objects.equals(dto.getStatus(), exam.getStatus())
                && !"closed".equals(dto.getStatus()))
            throw AppException.badRequest("已有答卷后只能关闭考试");
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
            List<Long> enrolledCourseIds = enrollmentMapper.selectList(
                            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, loginUser.getUserId()))
                    .stream().map(CourseEnrollment::getCourseId).toList();
            LambdaQueryWrapper<Exam> publishedWrapper = new LambdaQueryWrapper<Exam>()
                    .eq(Exam::getStatus, "published")
                    .and(w -> {
                        w.isNull(Exam::getTargetClass);
                        if (studentClass != null && !studentClass.isBlank()) w.or().eq(Exam::getTargetClass, studentClass);
                    })
                    .and(w -> {
                        w.isNull(Exam::getCourseId);
                        if (!enrolledCourseIds.isEmpty()) w.or().in(Exam::getCourseId, enrolledCourseIds);
                    })
                    .orderByDesc(Exam::getCreatedAt);
            List<Exam> published = examMapper.selectList(publishedWrapper);
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
    @Transactional
    public Map<String, Object> createExam(@RequestBody ExamCreateDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = new Exam();
        applyExamDTO(exam, dto, loginUser);
        examMapper.insert(exam);
        if (dto.getQuestions() != null) persistExamQuestions(exam.getId(), dto.getQuestions(), loginUser);
        exam.setTotalScore(computeTotalScore(exam.getId()));
        validateExamConfig(exam, false);
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
            if (!canStudentAccessExam(exam, loginUser.getUserId()))
                throw AppException.notFound("考试不存在");
            LocalDateTime now = now();
            if ("published".equals(exam.getStatus()) && exam.getOpenTime() != null
                    && now.isBefore(exam.getOpenTime()))
                throw AppException.badRequest("考试尚未开始");
            if ("closed".equals(exam.getStatus()) && examRecordMapper.selectCount(
                    new LambdaQueryWrapper<ExamRecord>()
                            .eq(ExamRecord::getExamId, examId)
                            .eq(ExamRecord::getStudentId, loginUser.getUserId())) == 0)
                throw AppException.notFound("考试不存在");
            if ("published".equals(exam.getStatus()) && exam.getCloseTime() != null
                    && !now.isBefore(exam.getCloseTime())
                    && examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                            .eq(ExamRecord::getExamId, examId)
                            .eq(ExamRecord::getStudentId, loginUser.getUserId())) == 0)
                throw AppException.notFound("考试不存在");
        } else if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权访问");
        List<ExamQuestion> eqs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId)
                        .orderByAsc(ExamQuestion::getOrderNum));
        Map<String, Object> result = examToMap(exam, eqs, true);
        if ("student".equals(loginUser.getRole())) {
            // Never expose the answer key through the exam-taking endpoint.
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
        return result;
    }

    @PutMapping("/{examId}")
    @Transactional
    public Map<String, Object> updateExam(@PathVariable Long examId, @RequestBody ExamUpdateDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = lockExam(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        validateCourseAccess(exam.getCourseId(), loginUser);
        boolean questionsChanged = examQuestionsChanged(examId, dto.getQuestions());
        ensureExamRulesMutable(exam, dto, questionsChanged);
        String oldStatus = exam.getStatus();
        applyExamUpdateDTO(exam, dto, loginUser);
        if (dto.getQuestions() != null && questionsChanged)
            persistExamQuestions(examId, dto.getQuestions(), loginUser);
        exam.setTotalScore(computeTotalScore(examId));
        validateExamConfig(exam, false);
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
    @Transactional
    public Map<String, Boolean> deleteExam(@PathVariable Long examId,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = lockExam(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        if ("teacher".equals(loginUser.getRole())
                && examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId)) > 0)
            throw AppException.badRequest("已有答卷的考试不能删除，请联系管理员");
        if ("admin".equals(loginUser.getRole())
                && examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId)) > 0)
            throw AppException.badRequest("已有答卷的考试不能删除");
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
    @Transactional
    public Map<String, Object> startExam(@PathVariable Long examId,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可参加考试");
        // Serialize starts for the same exam so concurrent requests cannot create duplicate attempts.
        Exam exam = examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getId, examId).last("FOR UPDATE"));
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!List.of("published", "closed").contains(exam.getStatus()))
            throw AppException.badRequest("考试未开放");
        LocalDateTime now = now();
        if (exam.getOpenTime() != null && now.isBefore(exam.getOpenTime()))
            throw AppException.badRequest("考试尚未开始");
        validateExamConfig(exam, true);
        if (!canStudentAccessExam(exam, loginUser.getUserId()))
            throw AppException.badRequest("您的班级不在此次考试范围内");

        List<ExamRecord> existing = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, loginUser.getUserId()));
        // Check for in-progress attempt
        for (ExamRecord r : existing) {
            if ("in_progress".equals(r.getStatus())) {
                boolean examDeadlineReached = "closed".equals(exam.getStatus())
                        || (exam.getCloseTime() != null && !now.isBefore(exam.getCloseTime()));
                if (examDeadlineReached || recordTimedOut(exam, r, now)) {
                    finalizeTimedOutRecord(exam, r);
                    return buildExpiredAttemptResponse(exam, r,
                            examDeadlineReached ? "exam_closed" : "time_limit");
                }
                // Return existing in-progress record
                return buildStartResponse(exam, r);
            }
        }
        if (!"published".equals(exam.getStatus()))
            throw AppException.badRequest("考试已关闭");
        if (exam.getCloseTime() != null && !now.isBefore(exam.getCloseTime()))
            throw AppException.badRequest("考试已结束");
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
        resp.put("status", record.getStatus());
        resp.put("started_at", record.getStartedAt());
        resp.put("time_limit", exam.getTimeLimit());
        resp.put("duration_minutes", exam.getTimeLimit());
        resp.put("questions", qList);
        resp.put("exam_questions", qList);
        resp.put("attempt_count", record.getAttemptCount());
        List<Map<String, Object>> draftAnswers = answerMapper.selectList(
                        new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()))
                .stream().map(answer -> {
                    Map<String, Object> draft = new LinkedHashMap<>();
                    draft.put("question_id", answer.getQuestionId());
                    draft.put("user_answer", answer.getUserAnswer());
                    return draft;
                }).collect(Collectors.toList());
        resp.put("draft_answers", draftAnswers);
        return resp;
    }

    private boolean recordTimedOut(Exam exam, ExamRecord record, LocalDateTime now) {
        return exam.getTimeLimit() != null && record.getStartedAt() != null
                && !now.isBefore(record.getStartedAt().plusMinutes(exam.getTimeLimit()));
    }

    private Map<String, Object> buildExpiredAttemptResponse(Exam exam, ExamRecord record, String reason) {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean resultReleased = canStudentSeeResult(exam, record);
        response.put("record_id", record.getId());
        response.put("id", record.getId());
        response.put("exam_id", exam.getId());
        response.put("status", record.getStatus());
        response.put("total_score", resultReleased ? record.getTotalScore() : null);
        response.put("result_released", resultReleased);
        response.put("essay_graded", record.getEssayGraded());
        response.put("submitted_at", record.getSubmittedAt());
        response.put("timed_out", true);
        response.put("expired_reason", reason);
        return response;
    }

    private Map<String, Object> buildCompletedAttemptResponse(Exam exam, ExamRecord record) {
        boolean resultReleased = canStudentSeeResult(exam, record);
        LocalDateTime submittedAt = record.getSubmittedAt();
        boolean closedAtSubmission = submittedAt != null && exam.getCloseTime() != null
                && !submittedAt.isBefore(exam.getCloseTime());
        boolean timeLimitReached = submittedAt != null && exam.getTimeLimit() != null
                && record.getStartedAt() != null
                && !submittedAt.isBefore(record.getStartedAt().plusMinutes(exam.getTimeLimit()));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("record_id", record.getId());
        response.put("id", record.getId());
        response.put("exam_id", exam.getId());
        response.put("status", record.getStatus());
        response.put("total_score", resultReleased ? record.getTotalScore() : null);
        response.put("result_released", resultReleased);
        response.put("essay_graded", record.getEssayGraded());
        response.put("submitted_at", record.getSubmittedAt());
        response.put("already_submitted", true);
        if (closedAtSubmission || timeLimitReached) {
            response.put("timed_out", true);
            response.put("expired_reason", closedAtSubmission ? "exam_closed" : "time_limit");
        }
        return response;
    }

    /** Finalize a timed-out attempt from the last server-saved draft. */
    private void finalizeTimedOutRecord(Exam exam, ExamRecord record) {
        List<ExamQuestion> questions = loadValidExamQuestions(exam.getId());
        Map<Long, String> savedDraft = answerMapper.selectList(
                        new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()))
                .stream().collect(Collectors.toMap(Answer::getQuestionId,
                        answer -> answer.getUserAnswer() != null ? answer.getUserAnswer() : "",
                        (first, ignored) -> first));
        answerMapper.delete(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()));
        boolean hasEssay = false;
        double totalScore = 0;
        for (ExamQuestion eq : questions) {
            Question q = questionMapper.selectById(eq.getQuestionId());
            Answer answer = new Answer();
            answer.setRecordId(record.getId());
            answer.setQuestionId(eq.getQuestionId());
            String userAnswer = savedDraft.getOrDefault(eq.getQuestionId(), "");
            answer.setUserAnswer(userAnswer);
            if (q != null && "essay".equals(q.getQuestionType())) {
                answer.setIsCorrect("P");
                hasEssay = true;
                answer.setScoreGot(0.0);
            } else {
                boolean correct = q != null && judgeAnswer(q, userAnswer);
                answer.setIsCorrect(correct ? "Y" : "N");
                double score = correct && eq.getScore() != null ? eq.getScore() : 0.0;
                answer.setScoreGot(score);
                totalScore += score;
            }
            answerMapper.insert(answer);
        }
        record.setStatus(hasEssay ? "submitted" : "graded");
        record.setSubmittedAt(now());
        record.setTotalScore(totalScore);
        record.setEssayGraded(!hasEssay);
        examRecordMapper.updateById(record);
    }

    @PutMapping("/{examId}/draft")
    @Transactional
    public Map<String, Object> saveExamDraft(@PathVariable Long examId,
                                               @Valid @RequestBody ExamSubmitDTO dto,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可保存答题草稿");
        // A shared exam lock keeps status/deadline/config stable while allowing
        // different students in the same exam to save drafts concurrently.
        Exam exam = shareLockExam(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        ExamRecord record = examRecordMapper.selectOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getId, dto.getRecordId())
                .eq(ExamRecord::getExamId, examId)
                .eq(ExamRecord::getStudentId, loginUser.getUserId())
                .last("FOR UPDATE"));
        if (record == null) throw AppException.notFound("考试记录不存在");
        if (!"in_progress".equals(record.getStatus())) throw AppException.badRequest("考试记录状态不正确");
        if (!"published".equals(exam.getStatus())) throw AppException.badRequest("考试已关闭");
        LocalDateTime currentTime = now();
        if ((exam.getCloseTime() != null && !currentTime.isBefore(exam.getCloseTime()))
                || recordTimedOut(exam, record, currentTime))
            throw AppException.badRequest("考试已截止");
        List<ExamQuestion> examQuestions = loadValidExamQuestions(examId);
        Set<Long> examQuestionIds = examQuestions.stream()
                .map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        Map<Long, String> submittedAnswers = new LinkedHashMap<>();
        for (AnswerItemDTO item : dto.getAnswers()) {
            if (!examQuestionIds.contains(item.getQuestionId()))
                throw AppException.badRequest("草稿包含不属于本考试的题目");
            if (submittedAnswers.put(item.getQuestionId(),
                    item.getUserAnswer() != null ? item.getUserAnswer() : "") != null)
                throw AppException.badRequest("同一道题不能重复保存");
        }

        answerMapper.delete(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()));
        for (ExamQuestion eq : examQuestions) {
            Answer draft = new Answer();
            draft.setRecordId(record.getId());
            draft.setQuestionId(eq.getQuestionId());
            draft.setUserAnswer(submittedAnswers.getOrDefault(eq.getQuestionId(), ""));
            draft.setIsCorrect(null);
            draft.setScoreGot(0.0);
            answerMapper.insert(draft);
        }
        return Map.of("ok", true, "saved_at", currentTime);
    }

    @PostMapping("/{examId}/submit")
    @Transactional
    public Map<String, Object> submitExam(@PathVariable Long examId, @Valid @RequestBody ExamSubmitDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可提交");
        // Submissions only read exam rules. A shared lock prevents rule/status
        // changes while allowing different students to submit concurrently.
        Exam exam = shareLockExam(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        ExamRecord record = examRecordMapper.selectOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getId, dto.getRecordId())
                .eq(ExamRecord::getExamId, examId)
                .eq(ExamRecord::getStudentId, loginUser.getUserId())
                .last("FOR UPDATE"));
        if (record == null)
            throw AppException.notFound("考试记录不存在");
        if (!"in_progress".equals(record.getStatus())) {
            if (List.of("submitted", "graded").contains(record.getStatus()))
                return buildCompletedAttemptResponse(exam, record);
            throw AppException.badRequest("考试记录状态不正确");
        }
        User currentStudent = userMapper.selectById(loginUser.getUserId());
        if (currentStudent == null || !Boolean.TRUE.equals(currentStudent.getIsActive()))
            throw AppException.forbidden("账号已被禁用");
        LocalDateTime now = now();
        boolean examDeadlineReached = "closed".equals(exam.getStatus())
                || (exam.getCloseTime() != null && !now.isBefore(exam.getCloseTime()));
        if (examDeadlineReached || recordTimedOut(exam, record, now)) {
            finalizeTimedOutRecord(exam, record);
            return buildExpiredAttemptResponse(exam, record,
                    examDeadlineReached ? "exam_closed" : "time_limit");
        }
        if (!"published".equals(exam.getStatus())) throw AppException.badRequest("考试未开放");
        List<ExamQuestion> examQuestions = loadValidExamQuestions(examId);

        Map<Long, ExamQuestion> qMap = new LinkedHashMap<>();
        for (ExamQuestion eq : examQuestions) {
            if (eq.getQuestionId() == null || qMap.put(eq.getQuestionId(), eq) != null)
                throw AppException.badRequest("考试题目配置无效");
        }
        Map<Long, AnswerItemDTO> submittedByQuestion = new LinkedHashMap<>();
        for (AnswerItemDTO item : dto.getAnswers()) {
            if (item == null || item.getQuestionId() == null)
                throw AppException.badRequest("答题题目不能为空");
            if (!qMap.containsKey(item.getQuestionId()))
                throw AppException.badRequest("提交内容包含不属于本考试的题目");
            if (submittedByQuestion.put(item.getQuestionId(), item) != null)
                throw AppException.badRequest("同一道题不能重复提交");
        }

        double totalScore = 0;
        boolean hasEssay = false;
        List<Answer> answerList = new ArrayList<>();

        // Build one persisted answer per configured exam question. Missing answers are
        // treated as blank, so omitting an essay cannot bypass manual grading.
        for (ExamQuestion eq : examQuestions) {
            Question q = questionMapper.selectById(eq.getQuestionId());
            if (q == null) throw AppException.badRequest("考试题目不存在");
            AnswerItemDTO item = submittedByQuestion.get(eq.getQuestionId());
            String userAnswer = item != null && item.getUserAnswer() != null ? item.getUserAnswer() : "";

            Answer ans = new Answer();
            ans.setRecordId(record.getId()); ans.setQuestionId(eq.getQuestionId());
            ans.setUserAnswer(userAnswer);

            String isCorrect; double scoreGot = 0;
            if ("essay".equals(q.getQuestionType())) {
                isCorrect = "P";
                hasEssay = true;
            } else {
                isCorrect = judgeAnswer(q, userAnswer) ? "Y" : "N";
                if ("Y".equals(isCorrect)) {
                    scoreGot = eq.getScore() != null ? eq.getScore() : 0;
                    totalScore += scoreGot;
                }
            }
            ans.setIsCorrect(isCorrect); ans.setScoreGot(scoreGot);
            answerList.add(ans);
        }
        answerMapper.delete(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, record.getId()));
        for (Answer ans : answerList) answerMapper.insert(ans);

        record.setStatus(hasEssay ? "submitted" : "graded");
        record.setSubmittedAt(now());
        record.setTotalScore(totalScore);
        record.setEssayGraded(!hasEssay);
        examRecordMapper.updateById(record);

        boolean releaseScore = canStudentSeeResult(exam, record);
        if ("graded".equals(record.getStatus()) && canStudentSeeResult(exam, record)) {
            sendNotification(loginUser.getUserId(),
                    "您的考试《" + exam.getTitle() + "》已完成评分，得分：" + totalScore, "grade");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("record_id", record.getId());
        response.put("status", record.getStatus());
        response.put("total_score", releaseScore ? totalScore : null);
        response.put("result_released", releaseScore);
        response.put("essay_graded", record.getEssayGraded());
        response.put("submitted_at", record.getSubmittedAt());
        return response;
    }

    private boolean judgeAnswer(Question q, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String correct = q.getAnswer();
        if (correct == null || correct.isBlank()) return false;
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
            if (!List.of("published", "closed").contains(exam.getStatus())
                    || !canStudentAccessExam(exam, loginUser.getUserId()))
                throw AppException.notFound("考试不存在");
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
            boolean showResult = !"student".equals(loginUser.getRole()) || canStudentSeeResult(exam, r);
            m.put("submitted_at", r.getSubmittedAt()); m.put("total_score", showResult ? r.getTotalScore() : null);
            m.put("result_released", showResult);
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
                boolean showAnswer = !"student".equals(loginUser.getRole())
                        || canStudentSeeAnswerKey(exam, r);
                am.put("correct_answer", showAnswer && q != null ? q.getAnswer() : null);
                am.put("is_correct", showResult && ans != null ? ans.getIsCorrect() : null);
                am.put("score_got", showResult && ans != null ? ans.getScoreGot() : null);
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
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("student".equals(loginUser.getRole())
                && (!List.of("published", "closed").contains(exam.getStatus())
                || !canStudentAccessExam(exam, loginUser.getUserId())))
            throw AppException.notFound("考试不存在");
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
        boolean showResult = !"student".equals(loginUser.getRole()) || canStudentSeeResult(exam, record);
        m.put("total_score", showResult ? record.getTotalScore() : null);
        m.put("result_released", showResult);
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
        boolean showAnswer = !"student".equals(loginUser.getRole())
                || canStudentSeeAnswerKey(exam, record);
        List<Map<String, Object>> answers = examQs.stream().map(eq -> {
            Question q = questionMap.get(eq.getQuestionId());
            Answer ans = answerByQid.get(eq.getQuestionId());
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", ans != null ? ans.getId() : null);
            am.put("question_id", eq.getQuestionId());
            am.put("question_title", q != null ? q.getTitle() : null);
            am.put("question_type", q != null ? q.getQuestionType() : null);
            am.put("user_answer", ans != null ? ans.getUserAnswer() : null);
            am.put("correct_answer", showAnswer && q != null ? q.getAnswer() : null);
            am.put("is_correct", showResult && ans != null ? ans.getIsCorrect() : null);
            am.put("score_got", showResult && ans != null ? ans.getScoreGot() : null);
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
        if ("student".equals(loginUser.getRole()) && (!List.of("published", "closed").contains(exam.getStatus())
                || !canStudentAccessExam(exam, loginUser.getUserId())))
            throw AppException.notFound("考试不存在");
        else if (!canAccessExam(exam, loginUser) && !"student".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");

        List<Answer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, recordId));
        List<ExamQuestion> examQs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId).orderByAsc(ExamQuestion::getOrderNum));
        Map<Long, Answer> answerMap = answers.stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> a, (a, b) -> a));

        boolean showAnswer = !"student".equals(loginUser.getRole())
                || canStudentSeeAnswerKey(exam, record);
        boolean showResult = !"student".equals(loginUser.getRole())
                || canStudentSeeResult(exam, record);

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
                qm.put("is_correct", showResult ? ans.getIsCorrect() : null);
                qm.put("score_got", showResult ? ans.getScoreGot() : null);
            } else { qm.put("user_answer", null); qm.put("is_correct", null); qm.put("score_got", null); }
            return qm;
        }).collect(Collectors.toList());

        User student = userMapper.selectById(record.getStudentId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId()); result.put("exam_id", record.getExamId());
        result.put("student_id", record.getStudentId());
        result.put("student_name", student != null ? (student.getRealName() != null ? student.getRealName() : student.getUsername()) : null);
        result.put("class_name", student != null ? student.getClassName() : null);
        result.put("status", record.getStatus()); result.put("started_at", record.getStartedAt());
        result.put("submitted_at", record.getSubmittedAt()); result.put("total_score", showResult ? record.getTotalScore() : null);
        result.put("result_released", showResult);
        result.put("attempt_count", record.getAttemptCount()); result.put("essay_graded", record.getEssayGraded());
        result.put("questions", qDetails);
        return result;
    }

    @PutMapping("/{examId}/records/{recordId}/grade")
    @Transactional
    public Map<String, Object> gradeEssay(@PathVariable Long examId, @PathVariable Long recordId,
                                           @Valid @RequestBody EssayGradeDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        ExamRecord record = examRecordMapper.selectOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getId, recordId).last("FOR UPDATE"));
        if (record == null || !record.getExamId().equals(examId)) throw AppException.notFound("考试记录不存在");
        if (!"submitted".equals(record.getStatus())) throw AppException.badRequest("该记录无需批改");

        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        Map<Long, ExamQuestion> eqByQuestionId = examQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, eq -> eq, (a, b) -> a));
        List<Answer> recordAnswers = answerMapper.selectList(
                new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, recordId));
        Map<Long, Answer> answerById = recordAnswers.stream()
                .collect(Collectors.toMap(Answer::getId, a -> a, (a, b) -> a));
        Set<Long> essayAnswerIds = recordAnswers.stream()
                .filter(a -> {
                    ExamQuestion eq = eqByQuestionId.get(a.getQuestionId());
                    Question q = eq != null ? questionMapper.selectById(a.getQuestionId()) : null;
                    return q != null && "essay".equals(q.getQuestionType());
                })
                .map(Answer::getId).collect(Collectors.toSet());
        if (essayAnswerIds.isEmpty()) throw AppException.badRequest("该答卷没有待批改的简答题");
        if (dto.getGrades().size() != essayAnswerIds.size())
            throw AppException.badRequest("必须一次提交全部简答题的批改结果");

        Set<Long> gradedIds = new HashSet<>();
        for (EssayGradeDTO.EssayGradeItemDTO item : dto.getGrades()) {
            if (!gradedIds.add(item.getAnswerId()) || !essayAnswerIds.contains(item.getAnswerId()))
                throw AppException.badRequest("批改内容包含重复或无效的答案");
            Answer ans = answerById.get(item.getAnswerId());
            ExamQuestion eq = ans != null ? eqByQuestionId.get(ans.getQuestionId()) : null;
            double maxScore = eq != null && eq.getScore() != null ? eq.getScore() : 0;
            double scoreGot = item.getScoreGot();
            if (eq == null || !Double.isFinite(scoreGot) || scoreGot < 0 || scoreGot > maxScore)
                throw AppException.badRequest("简答题得分必须在 0 到该题分值之间");
            if ("Y".equals(item.getIsCorrect()) && Double.compare(scoreGot, maxScore) != 0)
                throw AppException.badRequest("判定为正确时必须给满分");
            if ("N".equals(item.getIsCorrect()) && Double.compare(scoreGot, 0) != 0)
                throw AppException.badRequest("判定为错误时得分必须为 0");
            ans.setIsCorrect(item.getIsCorrect()); ans.setScoreGot(scoreGot);
            answerMapper.updateById(ans);
        }
        if (gradedIds.size() != essayAnswerIds.size())
            throw AppException.badRequest("简答题批改结果不完整");
        // Recalculate from persisted answer rows so repeated grading cannot double-count points.
        double totalScoreCalc = answerMapper.selectList(new LambdaQueryWrapper<Answer>().eq(Answer::getRecordId, recordId))
                .stream().mapToDouble(a -> a.getScoreGot() != null ? a.getScoreGot() : 0).sum();
        record.setTotalScore(totalScoreCalc);
        record.setStatus("graded"); record.setEssayGraded(true);
        examRecordMapper.updateById(record);
        String gradeMessage = canStudentSeeResult(exam, record)
                ? "您的考试《" + exam.getTitle() + "》已批改完成，得分：" + totalScoreCalc
                : "您的考试《" + exam.getTitle() + "》已完成批改，成绩将在考试结束后公布";
        sendNotification(record.getStudentId(), gradeMessage, "grade");
        return Map.of("ok", true, "total_score", totalScoreCalc, "essay_graded", true);
    }

    @GetMapping("/{examId}/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathVariable Long examId,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("student".equals(loginUser.getRole())) {
            if (!List.of("published", "closed").contains(exam.getStatus())
                    || !canStudentAccessExam(exam, loginUser.getUserId()))
                throw AppException.notFound("考试不存在");
            LocalDateTime now = now();
            if (exam.getOpenTime() != null && now.isBefore(exam.getOpenTime()))
                throw AppException.notFound("考试不存在");
            if ("published".equals(exam.getStatus())
                    && (exam.getCloseTime() == null || now.isBefore(exam.getCloseTime())))
                throw AppException.forbidden("考试成绩尚未发布");
            if (!Boolean.TRUE.equals(exam.getScorePublic())) throw AppException.forbidden("成绩未公开");
        } else if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权访问");

        LambdaQueryWrapper<ExamRecord> leaderboardWrapper = new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId)
                .orderByDesc(ExamRecord::getTotalScore).orderByAsc(ExamRecord::getSubmittedAt);
        if ("student".equals(loginUser.getRole()))
            leaderboardWrapper.eq(ExamRecord::getStatus, "graded");
        else
            leaderboardWrapper.in(ExamRecord::getStatus, "graded", "submitted");
        List<ExamRecord> records = examRecordMapper.selectList(leaderboardWrapper);
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
        String ts = now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        byte[] bytes = excelService.export(headers, rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"grades_" + examId + "_" + ts + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/{examId}/grades/import")
    @Transactional
    public Map<String, Object> importGrades(@PathVariable Long examId,
                                             @RequestParam("file") MultipartFile file,
                                             @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权操作");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if (!canAccessExam(exam, loginUser)) throw AppException.forbidden("无权操作");
        if (file == null || file.isEmpty()) throw AppException.badRequest("上传文件不能为空");
        double maxScore = exam.getTotalScore() != null ? exam.getTotalScore() : computeTotalScore(examId);
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
                if (!Double.isFinite(score) || score < 0 || score > maxScore)
                    throw new IllegalArgumentException("成绩必须在 0 到考试总分之间");
                List<ExamRecord> recs = examRecordMapper.selectList(
                        new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                                .eq(ExamRecord::getStudentId, sid)
                                .in(ExamRecord::getStatus, "graded", "submitted")
                                .orderByDesc(ExamRecord::getSubmittedAt).last("LIMIT 1"));
                if (recs.isEmpty()) throw new IllegalArgumentException("找不到该学生的考试记录");
                ExamRecord r = recs.get(0);
                r.setTotalScore(score); r.setStatus("graded"); r.setEssayGraded(true);
                examRecordMapper.updateById(r);
                String gradeMessage = canStudentSeeResult(exam, r)
                        ? "您的考试《" + exam.getTitle() + "》成绩已更新：" + score
                        : "您的考试《" + exam.getTitle() + "》成绩已更新，成绩将在考试结束后公布";
                sendNotification(sid, gradeMessage, "grade");
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
        validateCourseAccess(dto.getCourseId(), loginUser);
        if ("admin".equals(loginUser.getRole())) validateTeacherAssignment(dto.getTeacherId());
        exam.setTitle(dto.getTitle()); exam.setDescription(dto.getDescription());
        exam.setTeacherId("admin".equals(loginUser.getRole()) && dto.getTeacherId() != null ?
                dto.getTeacherId() : loginUser.getUserId());
        exam.setCourseId(dto.getCourseId()); exam.setTargetClass(dto.getTargetClass());
        exam.setStatus(dto.getStatus() != null ? dto.getStatus() : "draft");
        exam.setOpenTime(dto.getOpenTime()); exam.setCloseTime(dto.getCloseTime());
        exam.setTimeLimit(dto.getTimeLimit() != null ? dto.getTimeLimit() : 60);
        exam.setMaxAttempts(dto.getMaxAttempts());
        exam.setAllowRetake(dto.getAllowRetake() != null ? dto.getAllowRetake() : false);
        exam.setScorePublic(dto.getScorePublic() != null ? dto.getScorePublic() : true);
        exam.setPassScore(dto.getPassScore()); exam.setTotalScore(0.0);
    }

    private void applyExamUpdateDTO(Exam exam, ExamUpdateDTO dto, LoginUser loginUser) {
        validateCourseAccess(dto.getCourseId(), loginUser);
        if ("admin".equals(loginUser.getRole())) validateTeacherAssignment(dto.getTeacherId());
        if (dto.getTitle() != null) exam.setTitle(dto.getTitle());
        if (dto.getDescription() != null) exam.setDescription(dto.getDescription());
        if ("admin".equals(loginUser.getRole()) && dto.getTeacherId() != null) exam.setTeacherId(dto.getTeacherId());
        if (dto.isCourseIdPresent()) exam.setCourseId(dto.getCourseId());
        if (dto.isTargetClassPresent()) exam.setTargetClass(dto.getTargetClass());
        if (dto.getStatus() != null) exam.setStatus(dto.getStatus());
        if (dto.isOpenTimePresent()) exam.setOpenTime(dto.getOpenTime());
        if (dto.isCloseTimePresent()) exam.setCloseTime(dto.getCloseTime());
        if (dto.getTimeLimit() != null) exam.setTimeLimit(dto.getTimeLimit());
        if (dto.getMaxAttempts() != null) exam.setMaxAttempts(dto.getMaxAttempts());
        if (dto.getAllowRetake() != null) exam.setAllowRetake(dto.getAllowRetake());
        if (dto.getScorePublic() != null) exam.setScorePublic(dto.getScorePublic());
        if (dto.getPassScore() != null) exam.setPassScore(dto.getPassScore());
    }
}
