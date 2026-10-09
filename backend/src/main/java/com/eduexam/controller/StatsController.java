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
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ExamMapper examMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;

    @GetMapping("/categories")
    public List<Map<String, Object>> categoryStats(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if ("teacher".equals(loginUser.getRole())) wrapper.eq(Question::getCreatorId, loginUser.getUserId());
        List<Question> questions = questionMapper.selectList(wrapper);
        Map<Long, Long> countByCat = questions.stream()
                .filter(q -> q.getCategoryId() != null)
                .collect(Collectors.groupingBy(Question::getCategoryId, Collectors.counting()));
        List<Category> allCats = categoryMapper.selectList(null);
        return allCats.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("category_name", c.getName());
            m.put("parent_id", c.getParentId());
            m.put("question_count", countByCat.getOrDefault(c.getId(), 0L));
            return m;
        }).filter(m -> (Long) m.get("question_count") > 0)
                .sorted(Comparator.comparingLong(m -> -(Long) m.get("question_count")))
                .collect(Collectors.toList());
    }

    @GetMapping("/exams")
    public Map<String, Object> examStats(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<Exam> examWrapper = new LambdaQueryWrapper<>();
        if ("teacher".equals(loginUser.getRole())) examWrapper.eq(Exam::getTeacherId, loginUser.getUserId());
        List<Exam> exams = examMapper.selectList(examWrapper);
        long total = exams.size();
        long published = exams.stream().filter(e -> "published".equals(e.getStatus())).count();
        long closed = exams.stream().filter(e -> "closed".equals(e.getStatus())).count();
        long draft = exams.stream().filter(e -> "draft".equals(e.getStatus())).count();
        List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
        long totalRecords = examIds.isEmpty() ? 0 :
                examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>().in(ExamRecord::getExamId, examIds));
        long gradedRecords = examIds.isEmpty() ? 0 :
                examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>().in(ExamRecord::getExamId, examIds)
                        .eq(ExamRecord::getStatus, "graded"));
        return Map.of("total_exams", total, "published", published, "closed", closed, "draft", draft,
                "total_records", totalRecords, "graded_records", gradedRecords);
    }

    @GetMapping("/exam/{examId}")
    public Map<String, Object> examSummary(@PathVariable Long examId,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("student".equals(loginUser.getRole()) && !Boolean.TRUE.equals(exam.getScorePublic()))
            throw AppException.forbidden("成绩统计未公开");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(exam.getTeacherId()))
            throw AppException.forbidden("无权访问");

        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                        .in(ExamRecord::getStatus, "graded", "submitted"));

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("total_participants", 0); empty.put("avg_score", 0);
        empty.put("pass_rate", 0.0); empty.put("good_rate", 0.0);
        empty.put("excellent_rate", 0.0); empty.put("max_score", 0);
        empty.put("score_distribution", buildEmptyDistribution());
        if (records.isEmpty()) return empty;

        List<Double> scores = records.stream().filter(r -> r.getTotalScore() != null)
                .map(ExamRecord::getTotalScore).collect(Collectors.toList());
        int total = scores.size();
        double avg = total > 0 ? scores.stream().mapToDouble(Double::doubleValue).average().orElse(0) : 0;
        double max = total > 0 ? scores.stream().mapToDouble(Double::doubleValue).max().orElse(0) : 0;
        double totalScore = exam.getTotalScore() != null && exam.getTotalScore() > 0 ? exam.getTotalScore() : 100.0;
        double passScore = exam.getPassScore() != null ? exam.getPassScore() : totalScore * 0.6;
        double goodScore = totalScore * 0.75;
        double excellentScore = totalScore * 0.9;

        long passCount = scores.stream().filter(s -> s >= passScore).count();
        long goodCount = scores.stream().filter(s -> s >= goodScore).count();
        long excellentCount = scores.stream().filter(s -> s >= excellentScore).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_participants", total);
        result.put("avg_score", Math.round(avg * 10) / 10.0);
        result.put("pass_rate", total > 0 ? Math.round((double) passCount / total * 1000) / 1000.0 : 0.0);
        result.put("good_rate", total > 0 ? Math.round((double) goodCount / total * 1000) / 1000.0 : 0.0);
        result.put("excellent_rate", total > 0 ? Math.round((double) excellentCount / total * 1000) / 1000.0 : 0.0);
        result.put("max_score", max);
        result.put("score_distribution", buildDistribution(scores, totalScore));
        return result;
    }

    @GetMapping("/exam/{examId}/category-distribution")
    public List<Map<String, Object>> examCategoryDist(@PathVariable Long examId,
                                                       @AuthenticationPrincipal LoginUser loginUser) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(exam.getTeacherId()))
            throw AppException.forbidden("无权访问");

        List<ExamQuestion> examQs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        Set<Long> questionIds = examQs.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        if (questionIds.isEmpty()) return List.of();

        List<Question> questions = questionMapper.selectBatchIds(new ArrayList<>(questionIds));
        Map<Long, Long> countByCat = questions.stream()
                .filter(q -> q.getCategoryId() != null)
                .collect(Collectors.groupingBy(Question::getCategoryId, Collectors.counting()));
        if (countByCat.isEmpty()) return List.of();

        List<Category> cats = categoryMapper.selectBatchIds(new ArrayList<>(countByCat.keySet()));
        return cats.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category_name", c.getName());
            m.put("question_count", countByCat.getOrDefault(c.getId(), 0L));
            return m;
        }).sorted(Comparator.comparingLong(m -> -(Long) m.get("question_count")))
                .collect(Collectors.toList());
    }

    @GetMapping("/exam/{examId}/insights")
    public Map<String, Object> examInsights(@PathVariable Long examId,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if ("student".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw AppException.notFound("考试不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(exam.getTeacherId()))
            throw AppException.forbidden("无权访问");

        List<ExamQuestion> examQs = examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId)
                        .orderByAsc(ExamQuestion::getOrderNum));
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId)
                        .in(ExamRecord::getStatus, "graded", "submitted"));

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("weak_points", List.of()); empty.put("question_accuracy", List.of());
        empty.put("class_comparison", List.of()); empty.put("student_comparison", List.of());
        if (records.isEmpty()) return empty;

        List<Long> recordIds = records.stream().map(ExamRecord::getId).collect(Collectors.toList());
        List<Answer> allAnswers = answerMapper.selectList(
                new LambdaQueryWrapper<Answer>().in(Answer::getRecordId, recordIds));
        Map<Long, List<Answer>> answersByQuestion = allAnswers.stream()
                .collect(Collectors.groupingBy(Answer::getQuestionId));

        Set<Long> questionIds = examQs.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Map.of() :
                questionMapper.selectBatchIds(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));

        List<Category> allCats = categoryMapper.selectList(null);
        Map<Long, String> catNames = allCats.stream().collect(Collectors.toMap(Category::getId, Category::getName));

        // Load tags per question
        Set<Long> allTagIds = new HashSet<>();
        Map<Long, List<Long>> tagIdsByQuestion = new HashMap<>();
        for (Long qid : questionIds) {
            List<Long> tids = questionTagMapper.findTagIdsByQuestionId(qid);
            tagIdsByQuestion.put(qid, tids);
            allTagIds.addAll(tids);
        }
        Map<Long, String> tagNameMap = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            tagMapper.selectBatchIds(new ArrayList<>(allTagIds)).forEach(t -> tagNameMap.put(t.getId(), t.getName()));
        }
        Map<Long, List<String>> tagsByQuestion = new HashMap<>();
        tagIdsByQuestion.forEach((qid, tids) ->
                tagsByQuestion.put(qid, tids.stream()
                        .map(tid -> tagNameMap.getOrDefault(tid, ""))
                        .filter(s -> !s.isEmpty()).collect(Collectors.toList())));

        // question_accuracy
        List<Map<String, Object>> questionAccuracy = examQs.stream().map(eq -> {
            Question q = questionMap.get(eq.getQuestionId());
            List<Answer> qAnswers = answersByQuestion.getOrDefault(eq.getQuestionId(), List.of());
            long answerCount = qAnswers.size();
            long correctCount = qAnswers.stream().filter(a -> "Y".equals(a.getIsCorrect())).count();
            double avgScoreVal = qAnswers.isEmpty() ? 0 : qAnswers.stream()
                    .mapToDouble(a -> a.getScoreGot() != null ? a.getScoreGot() : 0).average().orElse(0);
            double accuracyRate = answerCount > 0 ? (double) correctCount / answerCount : 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question_id", eq.getQuestionId());
            m.put("question_title", q != null ? q.getTitle() : null);
            m.put("category_name", q != null && q.getCategoryId() != null ? catNames.get(q.getCategoryId()) : null);
            m.put("question_type", q != null ? q.getQuestionType() : null);
            m.put("answer_count", answerCount);
            m.put("correct_count", correctCount);
            m.put("avg_score", Math.round(avgScoreVal * 10) / 10.0);
            m.put("accuracy_rate", Math.round(accuracyRate * 1000) / 1000.0);
            m.put("tag_names", tagsByQuestion.getOrDefault(eq.getQuestionId(), List.of()));
            return m;
        }).collect(Collectors.toList());

        // weak_points by category
        Map<Long, List<Double>> scoreRatesByCat = new HashMap<>();
        for (ExamQuestion eq : examQs) {
            Question q = questionMap.get(eq.getQuestionId());
            if (q == null || q.getCategoryId() == null) continue;
            List<Answer> qAnswers = answersByQuestion.getOrDefault(eq.getQuestionId(), List.of());
            if (qAnswers.isEmpty()) continue;
            double rate = qAnswers.stream().filter(a -> "Y".equals(a.getIsCorrect())).count() / (double) qAnswers.size();
            scoreRatesByCat.computeIfAbsent(q.getCategoryId(), k -> new ArrayList<>()).add(rate);
        }
        List<Map<String, Object>> weakPoints = scoreRatesByCat.entrySet().stream().map(e -> {
            double avgRate = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", catNames.getOrDefault(e.getKey(), "未知分类"));
            m.put("avg_score_rate", Math.round(avgRate * 1000) / 1000.0);
            m.put("point_type", "category");
            return m;
        }).sorted(Comparator.comparingDouble(m -> (Double) m.get("avg_score_rate")))
                .limit(8).collect(Collectors.toList());

        // class_comparison
        Set<Long> studentIds = records.stream().map(ExamRecord::getStudentId).collect(Collectors.toSet());
        Map<Long, User> studentMap = userMapper.selectBatchIds(new ArrayList<>(studentIds)).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        double totalScore = exam.getTotalScore() != null && exam.getTotalScore() > 0 ? exam.getTotalScore() : 100.0;
        double passScore = exam.getPassScore() != null ? exam.getPassScore() : totalScore * 0.6;

        Map<String, List<ExamRecord>> recordsByClass = new HashMap<>();
        for (ExamRecord r : records) {
            User s = studentMap.get(r.getStudentId());
            String cls = s != null && s.getClassName() != null ? s.getClassName() : "未知班级";
            recordsByClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> classComparison = recordsByClass.entrySet().stream().map(e -> {
            List<Double> scores = e.getValue().stream().filter(r -> r.getTotalScore() != null)
                    .map(ExamRecord::getTotalScore).collect(Collectors.toList());
            double avg = scores.isEmpty() ? 0 : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double highest = scores.isEmpty() ? 0 : scores.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            long passCount = scores.stream().filter(s -> s >= passScore).count();
            double passRate = scores.isEmpty() ? 0 : (double) passCount / scores.size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("class_name", e.getKey());
            m.put("participant_count", e.getValue().size());
            m.put("avg_score", Math.round(avg * 10) / 10.0);
            m.put("highest_score", highest);
            m.put("pass_rate", Math.round(passRate * 1000) / 1000.0);
            return m;
        }).sorted(Comparator.comparing(m -> (String) m.get("class_name"))).collect(Collectors.toList());

        // student_comparison
        List<Double> allScores = records.stream().filter(r -> r.getTotalScore() != null)
                .map(ExamRecord::getTotalScore).collect(Collectors.toList());
        double avgScore = allScores.isEmpty() ? 0 : allScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        List<ExamRecord> sorted = records.stream().filter(r -> r.getTotalScore() != null)
                .sorted(Comparator.comparingDouble(ExamRecord::getTotalScore).reversed())
                .collect(Collectors.toList());
        List<Map<String, Object>> studentComparison = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ExamRecord r = sorted.get(i);
            User s = studentMap.get(r.getStudentId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", i + 1);
            m.put("record_id", r.getId());
            m.put("student_id", r.getStudentId());
            m.put("username", s != null ? s.getUsername() : null);
            m.put("real_name", s != null ? s.getRealName() : null);
            m.put("class_name", s != null ? s.getClassName() : null);
            m.put("total_score", r.getTotalScore());
            m.put("delta_vs_avg", Math.round((r.getTotalScore() - avgScore) * 10) / 10.0);
            studentComparison.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weak_points", weakPoints);
        result.put("question_accuracy", questionAccuracy);
        result.put("class_comparison", classComparison);
        result.put("student_comparison", studentComparison);
        return result;
    }

    private List<Map<String, Object>> buildEmptyDistribution() {
        String[] ranges = {"<60%", "60%-69%", "70%-79%", "80%-89%", "≥90%"};
        return Arrays.stream(ranges).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("range", r); m.put("count", 0L);
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildDistribution(List<Double> scores, double totalScore) {
        String[] ranges = {"<60%", "60%-69%", "70%-79%", "80%-89%", "≥90%"};
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String r : ranges) dist.put(r, 0L);
        for (double score : scores) {
            int pct = totalScore > 0 ? (int) Math.min(score / totalScore * 100, 100) : 0;
            String key;
            if (pct < 60) key = "<60%";
            else if (pct < 70) key = "60%-69%";
            else if (pct < 80) key = "70%-79%";
            else if (pct < 90) key = "80%-89%";
            else key = "≥90%";
            dist.merge(key, 1L, Long::sum);
        }
        return dist.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("range", e.getKey()); m.put("count", e.getValue());
            return m;
        }).collect(Collectors.toList());
    }
}
