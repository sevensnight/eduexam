package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.mapper.*;
import com.eduexam.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final ExamMapper examMapper;
    private final ExamRecordMapper examRecordMapper;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final WrongBookEntryMapper wrongBookMapper;
    private final UserMapper userMapper;

    @GetMapping("/my-records")
    public List<Map<String, Object>> myRecords(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getStudentId, loginUser.getUserId())
                        .orderByDesc(ExamRecord::getSubmittedAt));
        Set<Long> examIds = records.stream().map(ExamRecord::getExamId).collect(Collectors.toSet());
        Map<Long, Exam> examMap = examIds.isEmpty() ? Map.of() :
                examMapper.selectBatchIds(examIds).stream().collect(Collectors.toMap(Exam::getId, e -> e));
        return records.stream().map(r -> {
            Exam e = examMap.get(r.getExamId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("exam_id", r.getExamId());
            m.put("exam_title", e != null ? e.getTitle() : null);
            m.put("total_score_possible", e != null ? e.getTotalScore() : null);
            m.put("pass_score", e != null ? e.getPassScore() : null);
            m.put("score_public", e != null ? e.getScorePublic() : null);
            m.put("status", r.getStatus()); m.put("started_at", r.getStartedAt());
            m.put("submitted_at", r.getSubmittedAt());
            boolean released = "graded".equals(r.getStatus()) && canRevealAnswerKey(e);
            boolean isGraded = released;
            // Do not expose objective scores before the answer-release time.
            m.put("total_score", released ? r.getTotalScore() : null);
            m.put("result_released", released);
            m.put("attempt_count", r.getAttemptCount()); m.put("essay_graded", r.getEssayGraded());
            // passed: null only when not yet fully graded
            Double actualScore = r.getTotalScore(); Double ps = e != null ? e.getPassScore() : null;
            m.put("passed", isGraded && actualScore != null && ps != null ? actualScore >= ps : null);
            return m;
        }).collect(Collectors.toList());
    }

    // ======================== Wrong Book ========================

    @GetMapping("/wrong-book")
    public List<Map<String, Object>> wrongBook(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");

        List<WrongBookEntry> entries = wrongBookMapper.selectList(
                new LambdaQueryWrapper<WrongBookEntry>()
                        .eq(WrongBookEntry::getStudentId, loginUser.getUserId())
                        .orderByDesc(WrongBookEntry::getAddedAt));
        if (entries.isEmpty()) return List.of();

        Set<Long> questionIds = entries.stream().map(WrongBookEntry::getQuestionId).collect(Collectors.toSet());

        // Only fully graded records may reveal answer keys.  A submitted
        // record can still be followed by a retake, so exposing answers while
        // essay grading is pending would undermine the exam.
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, loginUser.getUserId())
                        .eq(ExamRecord::getStatus, "graded")
                        .orderByDesc(ExamRecord::getSubmittedAt));
        List<Long> recordIds = records.stream().map(ExamRecord::getId).collect(Collectors.toList());

        // Batch-load wrong answers for those records and question ids
        Map<Long, Map<Long, Answer>> answersByRecord = new HashMap<>();
        if (!recordIds.isEmpty()) {
            answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                    .in(Answer::getRecordId, recordIds)
                    .in(Answer::getQuestionId, new ArrayList<>(questionIds)))
                    .forEach(a -> answersByRecord
                            .computeIfAbsent(a.getRecordId(), k -> new HashMap<>())
                            .put(a.getQuestionId(), a));
        }

        // Map record → exam
        Map<Long, Long> examIdByRecord = records.stream()
                .collect(Collectors.toMap(ExamRecord::getId, ExamRecord::getExamId));
        Set<Long> examIds = records.stream().map(ExamRecord::getExamId).collect(Collectors.toSet());
        Map<Long, Exam> examMap = examIds.isEmpty() ? Map.of() :
                examMapper.selectBatchIds(examIds).stream().collect(Collectors.toMap(Exam::getId, e -> e));

        // For each question in wrong book, find the most recent wrong answer
        Map<Long, String> userAnswerByQid = new HashMap<>();
        Map<Long, String> examTitleByQid = new HashMap<>();
        for (ExamRecord r : records) {
            Exam exam = examMap.get(examIdByRecord.get(r.getId()));
            if (!canRevealAnswerKey(exam)) continue;
            Map<Long, Answer> byQ = answersByRecord.getOrDefault(r.getId(), Map.of());
            for (Long qid : questionIds) {
                if (userAnswerByQid.containsKey(qid)) continue;
                Answer ans = byQ.get(qid);
                if (ans != null && "N".equals(ans.getIsCorrect())) {
                    userAnswerByQid.put(qid, ans.getUserAnswer());
                    examTitleByQid.put(qid, exam != null ? exam.getTitle() : null);
                }
            }
        }

        Map<Long, Category> catMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        return entries.stream()
                .filter(wb -> userAnswerByQid.containsKey(wb.getQuestionId()))
                .map(wb -> {
            Question q = questionMapper.selectById(wb.getQuestionId());
            List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(wb.getQuestionId());
            List<Tag> tags = tagIds.isEmpty() ? List.of() : tagMapper.selectBatchIds(tagIds);
            boolean verifiedWrongAnswer = userAnswerByQid.containsKey(wb.getQuestionId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", wb.getId()); m.put("added_at", wb.getAddedAt()); m.put("question_id", wb.getQuestionId());
            if (q != null) {
                m.put("title", q.getTitle()); m.put("question_type", q.getQuestionType());
                m.put("difficulty", q.getDifficulty()); m.put("options", q.getOptions());
                // A manually inserted/stale wrong-book row must not become an
                // answer-key oracle. Only reveal the key for a verified wrong
                // answer submitted by this student.
                if (verifiedWrongAnswer) {
                    m.put("answer", q.getAnswer());
                    m.put("correct_answer", q.getAnswer());
                    m.put("explanation", q.getExplanation());
                }
                m.put("category_id", q.getCategoryId());
                Category cat = q.getCategoryId() != null ? catMap.get(q.getCategoryId()) : null;
                m.put("category_name", cat != null ? cat.getName() : null);
            }
            m.put("user_answer", userAnswerByQid.get(wb.getQuestionId()));
            m.put("exam_title", examTitleByQid.get(wb.getQuestionId()));
            m.put("tags", tags.stream().map(t -> Map.of("id", t.getId(), "name", t.getName(),
                    "color", t.getColor() != null ? t.getColor() : "#409EFF")).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/wrong-book/category-stats")
    public List<Map<String, Object>> wrongBookCategoryStats(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");
        List<WrongBookEntry> allEntries = wrongBookMapper.selectList(
                new LambdaQueryWrapper<WrongBookEntry>().eq(WrongBookEntry::getStudentId, loginUser.getUserId()));
        if (allEntries.isEmpty()) return List.of();
        Set<Long> candidateIds = allEntries.stream().map(WrongBookEntry::getQuestionId).collect(Collectors.toSet());
        List<Long> qIds = new ArrayList<>(releasedWrongQuestionIds(loginUser.getUserId(), candidateIds));
        if (qIds.isEmpty()) return List.of();
        List<Question> questions = questionMapper.selectBatchIds(qIds);
        Map<Long, Long> countByCat = questions.stream().filter(q -> q.getCategoryId() != null)
                .collect(Collectors.groupingBy(Question::getCategoryId, Collectors.counting()));
        List<Category> allCats = categoryMapper.selectList(null);
        Map<Long, Category> catMap = allCats.stream().collect(Collectors.toMap(Category::getId, c -> c));
        return countByCat.entrySet().stream().map(e -> {
            Category cat = catMap.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category_id", e.getKey()); m.put("question_count", e.getValue());
            m.put("count", e.getValue());
            m.put("name", cat != null ? cat.getName() : null);
            m.put("category_name", cat != null ? cat.getName() : null);
            m.put("parent_id", cat != null ? cat.getParentId() : null);
            return m;
        }).sorted(Comparator.comparingLong(m -> -((Long) m.get("count")))).collect(Collectors.toList());
    }

    @GetMapping("/wrong-book/ids")
    public Map<String, Object> wrongBookIds(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");
        Set<Long> candidateIds = wrongBookMapper.selectList(new LambdaQueryWrapper<WrongBookEntry>()
                .eq(WrongBookEntry::getStudentId, loginUser.getUserId())
                .select(WrongBookEntry::getQuestionId))
                .stream().map(WrongBookEntry::getQuestionId).collect(Collectors.toSet());
        List<Long> ids = new ArrayList<>(releasedWrongQuestionIds(loginUser.getUserId(), candidateIds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ids", ids);
        return result;
    }

    private Set<Long> releasedWrongQuestionIds(Long studentId, Set<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return Set.of();
        List<ExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getStudentId, studentId)
                .eq(ExamRecord::getStatus, "graded")
                .select(ExamRecord::getId, ExamRecord::getExamId));
        if (records.isEmpty()) return Set.of();
        Set<Long> examIds = records.stream().map(ExamRecord::getExamId).collect(Collectors.toSet());
        Map<Long, Exam> exams = examMapper.selectBatchIds(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));
        List<Long> releasedRecordIds = records.stream()
                .filter(r -> canRevealAnswerKey(exams.get(r.getExamId())))
                .map(ExamRecord::getId).collect(Collectors.toList());
        if (releasedRecordIds.isEmpty()) return Set.of();
        return answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                        .in(Answer::getRecordId, releasedRecordIds)
                        .in(Answer::getQuestionId, candidateIds)
                        .eq(Answer::getIsCorrect, "N")
                        .select(Answer::getQuestionId))
                .stream().map(Answer::getQuestionId).collect(Collectors.toSet());
    }

    @PostMapping("/wrong-book/{questionId}")
    public Map<String, Boolean> addToWrongBook(@PathVariable Long questionId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");
        if (questionMapper.selectById(questionId) == null) throw AppException.notFound("题目不存在");
        if (!hasIncorrectAnswer(loginUser.getUserId(), questionId))
            throw AppException.badRequest("只能收藏自己答错过的题目");
        long exists = wrongBookMapper.selectCount(new LambdaQueryWrapper<WrongBookEntry>()
                .eq(WrongBookEntry::getStudentId, loginUser.getUserId())
                .eq(WrongBookEntry::getQuestionId, questionId));
        if (exists == 0) {
            WrongBookEntry wb = new WrongBookEntry();
            wb.setStudentId(loginUser.getUserId()); wb.setQuestionId(questionId);
            wrongBookMapper.insert(wb);
        }
        return Map.of("ok", true);
    }

    private boolean hasIncorrectAnswer(Long studentId, Long questionId) {
        List<ExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, studentId)
                        .eq(ExamRecord::getStatus, "graded")
                        .select(ExamRecord::getId, ExamRecord::getExamId));
        if (records.isEmpty()) return false;
        Set<Long> examIds = records.stream().map(ExamRecord::getExamId).collect(Collectors.toSet());
        Map<Long, Exam> exams = examMapper.selectBatchIds(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));
        List<Long> recordIds = records.stream()
                .filter(r -> canRevealAnswerKey(exams.get(r.getExamId())))
                .map(ExamRecord::getId).collect(Collectors.toList());
        if (recordIds.isEmpty()) return false;
        return answerMapper.selectCount(new LambdaQueryWrapper<Answer>()
                .in(Answer::getRecordId, recordIds)
                .eq(Answer::getQuestionId, questionId)
                .eq(Answer::getIsCorrect, "N")) > 0;
    }

    private boolean canRevealAnswerKey(Exam exam) {
        if (exam == null) return false;
        return Boolean.TRUE.equals(exam.getScorePublic())
                && ("closed".equals(exam.getStatus())
                || (exam.getCloseTime() != null
                && !LocalDateTime.now(APP_ZONE).isBefore(exam.getCloseTime())));
    }

    @DeleteMapping("/wrong-book/{questionId}")
    public Map<String, Boolean> removeFromWrongBook(@PathVariable Long questionId,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");
        wrongBookMapper.delete(new LambdaQueryWrapper<WrongBookEntry>()
                .eq(WrongBookEntry::getStudentId, loginUser.getUserId())
                .eq(WrongBookEntry::getQuestionId, questionId));
        return Map.of("ok", true);
    }

    private Set<Long> getSubCategoryIds(Long rootId) {
        List<Category> allCats = categoryMapper.selectList(null);
        Map<Long, List<Category>> childrenMap = new HashMap<>();
        for (Category c : allCats) {
            if (c.getParentId() != null) childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        Set<Long> result = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(rootId); result.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            for (Category child : childrenMap.getOrDefault(id, List.of())) {
                if (result.add(child.getId())) queue.add(child.getId());
            }
        }
        return result;
    }
}
