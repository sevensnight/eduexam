package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.mapper.*;
import com.eduexam.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

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
            boolean isGraded = "graded".equals(r.getStatus());
            // total_score: always show student their own score (score_public only affects class stats/leaderboard)
            m.put("total_score", r.getTotalScore());
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

        // Load student's exam records (graded/submitted) ordered by most recent first
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, loginUser.getUserId())
                        .in(ExamRecord::getStatus, "graded", "submitted")
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
            Map<Long, Answer> byQ = answersByRecord.getOrDefault(r.getId(), Map.of());
            for (Long qid : questionIds) {
                if (userAnswerByQid.containsKey(qid)) continue;
                Answer ans = byQ.get(qid);
                if (ans != null && !"Y".equals(ans.getIsCorrect())) {
                    userAnswerByQid.put(qid, ans.getUserAnswer());
                    Exam exam = examMap.get(examIdByRecord.get(r.getId()));
                    examTitleByQid.put(qid, exam != null ? exam.getTitle() : null);
                }
            }
        }

        Map<Long, Category> catMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        return entries.stream().map(wb -> {
            Question q = questionMapper.selectById(wb.getQuestionId());
            List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(wb.getQuestionId());
            List<Tag> tags = tagIds.isEmpty() ? List.of() : tagMapper.selectBatchIds(tagIds);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", wb.getId()); m.put("added_at", wb.getAddedAt()); m.put("question_id", wb.getQuestionId());
            if (q != null) {
                m.put("title", q.getTitle()); m.put("question_type", q.getQuestionType());
                m.put("difficulty", q.getDifficulty()); m.put("options", q.getOptions());
                m.put("answer", q.getAnswer()); m.put("correct_answer", q.getAnswer()); m.put("explanation", q.getExplanation());
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
        List<Long> qIds = allEntries.stream().map(WrongBookEntry::getQuestionId).collect(Collectors.toList());
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
        List<Long> ids = wrongBookMapper.selectList(new LambdaQueryWrapper<WrongBookEntry>()
                .eq(WrongBookEntry::getStudentId, loginUser.getUserId())
                .select(WrongBookEntry::getQuestionId))
                .stream().map(WrongBookEntry::getQuestionId).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ids", ids);
        return result;
    }

    @PostMapping("/wrong-book/{questionId}")
    public Map<String, Boolean> addToWrongBook(@PathVariable Long questionId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        if (!"student".equals(loginUser.getRole())) throw AppException.forbidden("仅学生可访问");
        if (questionMapper.selectById(questionId) == null) throw AppException.notFound("题目不存在");
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
