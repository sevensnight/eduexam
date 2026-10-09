package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.dto.QuestionCreateDTO;
import com.eduexam.dto.QuestionQueryDTO;
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
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final ExcelService excelService;

    private static final Set<String> QUESTION_TYPES = Set.of("single", "multiple", "truefalse", "essay");
    private static final Set<String> DIFFICULTY_LEVELS = Set.of("easy", "medium", "hard");

    private Map<String, Object> questionToMap(Question q, Map<Long, Category> catMap, List<Tag> tags) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("title", q.getTitle());
        m.put("question_type", q.getQuestionType());
        m.put("difficulty", q.getDifficulty());
        m.put("options", q.getOptions());
        m.put("answer", q.getAnswer());
        m.put("explanation", q.getExplanation());
        m.put("usage_count", q.getUsageCount());
        m.put("category_id", q.getCategoryId());
        Category cat = q.getCategoryId() != null ? catMap.get(q.getCategoryId()) : null;
        m.put("category_name", cat != null ? cat.getName() : null);
        m.put("category_path", buildCategoryPath(q.getCategoryId(), catMap));
        m.put("creator_id", q.getCreatorId());
        m.put("created_at", q.getCreatedAt());
        m.put("updated_at", q.getUpdatedAt());
        m.put("tags", tags.stream().map(t -> Map.of("id", t.getId(), "name", t.getName(), "color", t.getColor() != null ? t.getColor() : "#409EFF")).collect(Collectors.toList()));
        return m;
    }

    private String buildCategoryPath(Long categoryId, Map<Long, Category> catMap) {
        if (categoryId == null || !catMap.containsKey(categoryId)) return null;
        List<String> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Category cur = catMap.get(categoryId);
        while (cur != null && !visited.contains(cur.getId())) {
            path.add(cur.getName());
            visited.add(cur.getId());
            cur = cur.getParentId() != null ? catMap.get(cur.getParentId()) : null;
        }
        Collections.reverse(path);
        return String.join(" / ", path);
    }

    private Map<Long, Category> loadAllCategories() {
        return categoryMapper.selectList(null).stream().collect(Collectors.toMap(Category::getId, c -> c));
    }

    private List<Tag> loadTagsForQuestion(Long questionId) {
        List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(questionId);
        if (tagIds.isEmpty()) return List.of();
        return tagMapper.selectBatchIds(tagIds);
    }

    private QuestionQueryDTO buildQuery(Long ownerId, Integer categoryId, String difficulty,
                                         String questionType, String keyword, Long tagId,
                                         String sortBy, String sortOrder) {
        QuestionQueryDTO q = new QuestionQueryDTO();
        q.setOwnerId(ownerId);
        q.setCategoryId(categoryId);
        q.setDifficulty(difficulty);
        q.setQuestionType(questionType);
        q.setKeyword(keyword != null ? keyword.strip() : null);
        q.setTagId(tagId);
        q.setSortBy(sortBy != null ? sortBy : "created_at");
        q.setSortOrder(sortOrder != null ? sortOrder.toLowerCase() : "desc");
        return q;
    }

    @GetMapping
    public Map<String, Object> listQuestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "category_id", required = false) Integer categoryId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(name = "question_type", required = false) String questionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "tag_id", required = false) Long tagId,
            @RequestParam(name = "sort_by", defaultValue = "created_at") String sortBy,
            @RequestParam(name = "sort_order", defaultValue = "desc") String sortOrder,
            @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Long ownerId = "teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null;
        QuestionQueryDTO query = buildQuery(ownerId, categoryId, difficulty, questionType, keyword, tagId, sortBy, sortOrder);
        Page<Question> pg = new Page<>(page, size);
        questionMapper.selectQuestionPage(pg, query);
        Map<Long, Category> catMap = loadAllCategories();
        List<Map<String, Object>> items = pg.getRecords().stream().map(q -> {
            List<Tag> tags = loadTagsForQuestion(q.getId());
            return questionToMap(q, catMap, tags);
        }).collect(Collectors.toList());
        long total = pg.getTotal();
        int pages = (int) Math.ceil((double) total / size);
        return Map.of("items", items, "total", total, "page", page, "size", size, "pages", pages);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportQuestions(
            @RequestParam(name = "category_id", required = false) Integer categoryId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(name = "question_type", required = false) String questionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "tag_id", required = false) Long tagId,
            @RequestParam(name = "sort_by", defaultValue = "created_at") String sortBy,
            @RequestParam(name = "sort_order", defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "xlsx") String format,
            @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Long ownerId = "teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null;
        QuestionQueryDTO query = buildQuery(ownerId, categoryId, difficulty, questionType, keyword, tagId, sortBy, sortOrder);
        Page<Question> pg = new Page<>(1, Integer.MAX_VALUE);
        questionMapper.selectQuestionPage(pg, query);
        Map<Long, Category> catMap = loadAllCategories();
        List<String> headers = List.of("id", "title", "question_type", "difficulty", "options", "answer",
                "explanation", "category_path", "tag_names", "usage_count", "created_at", "updated_at");
        List<Map<String, Object>> rows = pg.getRecords().stream().map(q -> {
            List<Tag> tags = loadTagsForQuestion(q.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", q.getId()); row.put("title", q.getTitle());
            row.put("question_type", q.getQuestionType()); row.put("difficulty", q.getDifficulty());
            row.put("options", q.getOptions()); row.put("answer", q.getAnswer());
            row.put("explanation", q.getExplanation());
            row.put("category_path", buildCategoryPath(q.getCategoryId(), catMap));
            row.put("tag_names", tags.stream().map(Tag::getName).collect(Collectors.joining(",")));
            row.put("usage_count", q.getUsageCount());
            row.put("created_at", q.getCreatedAt() != null ? q.getCreatedAt().toString() : "");
            row.put("updated_at", q.getUpdatedAt() != null ? q.getUpdatedAt().toString() : "");
            return row;
        }).collect(Collectors.toList());
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        byte[] bytes = excelService.export(headers, rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questions_export_" + ts + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/import")
    public Map<String, Object> importQuestions(@RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        List<Map<String, String>> rows = excelService.parse(file.getOriginalFilename(), file.getBytes());
        if (rows.isEmpty()) return Map.of("total", 0, "created", 0, "updated", 0, "errors", List.of());

        Map<String, Tag> tagCache = new HashMap<>();
        tagMapper.selectList(null).forEach(t -> tagCache.put(t.getName().toLowerCase(), t));
        Map<String, Category> catCache = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> {
            String key = (c.getParentId() == null ? "null" : c.getParentId()) + ":" + c.getName().toLowerCase();
            catCache.put(key, c);
        });
        int created = 0, updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNum = i + 2;
            try {
                String qType = coalesce(row, "question_type", "type", "题型", "single").strip().toLowerCase();
                if (!QUESTION_TYPES.contains(qType)) throw new IllegalArgumentException("question_type must be single/multiple/truefalse/essay");
                String diff = coalesce(row, "difficulty", "level", "难度", "medium").strip().toLowerCase();
                if (!DIFFICULTY_LEVELS.contains(diff)) throw new IllegalArgumentException("difficulty must be easy/medium/hard");
                String title = coalesce(row, "title", "question", "题目", "").strip();
                if (title.isEmpty()) throw new IllegalArgumentException("title is required");
                String rawAnswer = coalesce(row, "answer", "solution", "答案", "").strip();
                if (rawAnswer.isEmpty()) throw new IllegalArgumentException("answer is required");
                String answer = normalizeAnswer(rawAnswer, qType);
                String options = normalizeOptions(coalesce(row, "options", "choices", "选项", null), qType);
                String explanation = coalesce(row, "explanation", "analysis", "解析", null);
                explanation = explanation != null && explanation.isEmpty() ? null : explanation;
                String catPath = coalesce(row, "category_path", "category_name", "category", "分类", null);
                Long categoryId = resolveCategoryPath(catPath, catCache, loginUser);
                List<Long> tagIds = resolveTagNames(coalesce(row, "tag_names", "tags", "labels", "标签", null), tagCache, loginUser);

                String rawId = coalesce(row, "id", null);
                Long questionId = null;
                try { if (rawId != null && !rawId.isBlank()) questionId = Long.parseLong(rawId.strip()); } catch (NumberFormatException ignored) {}

                Question q = questionId != null ? questionMapper.selectById(questionId) : null;
                if (questionId != null && q != null && "teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
                    throw new IllegalArgumentException("teachers can only update their own questions");

                boolean isNew = q == null;
                if (isNew) { q = new Question(); q.setCreatorId(loginUser.getUserId()); q.setUsageCount(0); }
                q.setTitle(title); q.setQuestionType(qType); q.setDifficulty(diff);
                q.setOptions(options); q.setAnswer(answer); q.setExplanation(explanation);
                q.setCategoryId(categoryId);
                if (isNew) questionMapper.insert(q);
                else questionMapper.updateById(q);
                questionTagMapper.deleteByQuestionId(q.getId());
                for (Long tid : tagIds) questionTagMapper.insert(q.getId(), tid);
                if (isNew) created++; else updated++;
            } catch (IllegalArgumentException ex) {
                errors.add(Map.of("row", lineNum, "message", ex.getMessage()));
            }
        }
        return Map.of("total", rows.size(), "created", created, "updated", updated,
                "errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
    }

    @GetMapping("/{questionId}")
    public Map<String, Object> getQuestion(@PathVariable Long questionId,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Question q = questionMapper.selectById(questionId);
        if (q == null) throw AppException.notFound("Question not found");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
            throw AppException.notFound("Question not found");
        return questionToMap(q, loadAllCategories(), loadTagsForQuestion(questionId));
    }

    @PostMapping
    public Map<String, Object> createQuestion(@RequestBody QuestionCreateDTO data,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Question q = new Question();
        q.setTitle(data.getTitle()); q.setQuestionType(data.getQuestionType());
        q.setDifficulty(data.getDifficulty()); q.setOptions(data.getOptions());
        q.setAnswer(data.getAnswer()); q.setExplanation(data.getExplanation());
        q.setCategoryId(data.getCategoryId() != null ? data.getCategoryId().longValue() : null);
        q.setCreatorId(loginUser.getUserId()); q.setUsageCount(0);
        questionMapper.insert(q);
        if (data.getTagIds() != null) {
            for (Long tagId : data.getTagIds()) questionTagMapper.insert(q.getId(), tagId);
        }
        return questionToMap(q, loadAllCategories(), loadTagsForQuestion(q.getId()));
    }

    @PutMapping("/{questionId}")
    public Map<String, Object> updateQuestion(@PathVariable Long questionId,
                                               @RequestBody QuestionCreateDTO data,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Question q = questionMapper.selectById(questionId);
        if (q == null) throw AppException.notFound("Question not found");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
            throw AppException.notFound("Question not found");
        if (data.getTitle() != null) q.setTitle(data.getTitle());
        if (data.getQuestionType() != null) q.setQuestionType(data.getQuestionType());
        if (data.getDifficulty() != null) q.setDifficulty(data.getDifficulty());
        if (data.getOptions() != null) q.setOptions(data.getOptions());
        if (data.getAnswer() != null) q.setAnswer(data.getAnswer());
        if (data.getExplanation() != null) q.setExplanation(data.getExplanation());
        if (data.getCategoryId() != null) q.setCategoryId(data.getCategoryId().longValue());
        questionMapper.updateById(q);
        if (data.getTagIds() != null) {
            questionTagMapper.deleteByQuestionId(questionId);
            for (Long tagId : data.getTagIds()) questionTagMapper.insert(questionId, tagId);
        }
        return questionToMap(q, loadAllCategories(), loadTagsForQuestion(questionId));
    }

    @DeleteMapping("/{questionId}")
    public Map<String, Boolean> deleteQuestion(@PathVariable Long questionId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Question q = questionMapper.selectById(questionId);
        if (q == null) throw AppException.notFound("Question not found");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
            throw AppException.notFound("Question not found");
        questionTagMapper.deleteByQuestionId(questionId);
        questionMapper.deleteById(questionId);
        return Map.of("ok", true);
    }

    private String coalesce(Map<String, String> row, String... keys) {
        for (String key : keys) {
            if (key == null) continue;
            String val = row.get(key);
            if (val != null) return val;
        }
        return "";
    }

    private String normalizeOptions(String raw, String qType) {
        if (!Set.of("single", "multiple").contains(qType)) return null;
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("options are required for single/multiple questions");
        return raw.strip();
    }

    private String normalizeAnswer(String raw, String qType) {
        if ("multiple".equals(qType)) {
            String[] parts = raw.split(",");
            return Arrays.stream(parts).map(String::trim).map(String::toUpperCase)
                    .filter(p -> !p.isEmpty()).sorted().collect(Collectors.joining(","));
        }
        if ("truefalse".equals(qType)) {
            String v = raw.toLowerCase();
            if (List.of("true", "t", "1", "yes", "y").contains(v)) return "True";
            if (List.of("false", "f", "0", "no", "n").contains(v)) return "False";
            throw new IllegalArgumentException("truefalse answers only accept True or False");
        }
        return raw;
    }

    private Long resolveCategoryPath(String path, Map<String, Category> cache, LoginUser loginUser) {
        if (path == null || path.isBlank()) return null;
        String[] parts = path.strip().split("[/>]");
        Long parentId = null;
        Category current = null;
        for (String part : parts) {
            String name = part.strip();
            if (name.isEmpty()) continue;
            String key = (parentId == null ? "null" : parentId) + ":" + name.toLowerCase();
            current = cache.get(key);
            if (current == null) {
                Category c = new Category();
                c.setName(name); c.setParentId(parentId);
                c.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
                long siblings = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                        .eq(parentId != null, Category::getParentId, parentId)
                        .isNull(parentId == null, Category::getParentId));
                c.setSortOrder((int) siblings);
                categoryMapper.insert(c);
                cache.put(key, c);
                current = c;
            }
            parentId = current.getId();
        }
        return current != null ? current.getId() : null;
    }

    private List<Long> resolveTagNames(String raw, Map<String, Tag> cache, LoginUser loginUser) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String name : raw.split("[,|/]+")) {
            String n = name.strip();
            if (n.isEmpty()) continue;
            Tag t = cache.get(n.toLowerCase());
            if (t == null) {
                t = new Tag(); t.setName(n); t.setColor("#409EFF");
                t.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
                tagMapper.insert(t);
                cache.put(n.toLowerCase(), t);
            }
            ids.add(t.getId());
        }
        return ids;
    }
}
