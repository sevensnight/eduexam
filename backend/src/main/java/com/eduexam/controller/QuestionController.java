package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduexam.common.AppException;
import com.eduexam.domain.*;
import com.eduexam.dto.QuestionCreateDTO;
import com.eduexam.dto.QuestionQueryDTO;
import com.eduexam.dto.QuestionTagRow;
import com.eduexam.mapper.*;
import com.eduexam.security.LoginUser;
import com.eduexam.service.ExcelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final ExcelService excelService;
    private final ObjectMapper objectMapper;

    private static final Set<String> QUESTION_TYPES = Set.of("single", "multiple", "truefalse", "essay");
    private static final Set<String> DIFFICULTY_LEVELS = Set.of("easy", "medium", "hard");

    private void lockCurrentManager(LoginUser loginUser) {
        User current = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, loginUser.getUserId()).last("FOR SHARE"));
        if (current == null || !Boolean.TRUE.equals(current.getIsActive())
                || !Set.of("admin", "teacher").contains(current.getRole()))
            throw AppException.forbidden("账号状态或角色已发生变化，请重新登录");
    }

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
        return loadTagsForQuestions(List.of(questionId)).getOrDefault(questionId, List.of());
    }

    private Map<Long, List<Tag>> loadTagsForQuestions(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return Map.of();
        Map<Long, List<Tag>> result = new HashMap<>();
        for (QuestionTagRow row : questionTagMapper.findTagsByQuestionIds(questionIds)) {
            Tag tag = new Tag();
            tag.setId(row.getId());
            tag.setName(row.getName());
            tag.setColor(row.getColor());
            tag.setCreatorId(row.getCreatorId());
            result.computeIfAbsent(row.getQuestionId(), ignored -> new ArrayList<>()).add(tag);
        }
        return result;
    }

    private boolean hasExamReferences(Long questionId) {
        return examQuestionMapper.selectCount(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getQuestionId, questionId)) > 0;
    }

    private Question lockQuestion(Long questionId) {
        return questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                .eq(Question::getId, questionId).last("FOR UPDATE"));
    }

    private QuestionQueryDTO buildQuery(Long ownerId, Integer categoryId, String difficulty,
                                         String questionType, String keyword, Long tagId,
                                         String sortBy, String sortOrder) {
        if (categoryId != null && categoryId <= 0) throw AppException.badRequest("分类ID必须为正整数");
        if (tagId != null && tagId <= 0) throw AppException.badRequest("标签ID必须为正整数");
        if (difficulty != null && !difficulty.isBlank() && !DIFFICULTY_LEVELS.contains(difficulty.toLowerCase(Locale.ROOT)))
            throw AppException.badRequest("难度筛选值不正确");
        if (questionType != null && !questionType.isBlank() && !QUESTION_TYPES.contains(questionType.toLowerCase(Locale.ROOT)))
            throw AppException.badRequest("题型筛选值不正确");
        Set<String> sortFields = Set.of("created_at", "updated_at", "usage_count", "title", "difficulty");
        if (sortBy == null || !sortFields.contains(sortBy)) throw AppException.badRequest("排序字段不正确");
        if (sortOrder == null || !Set.of("asc", "desc").contains(sortOrder.toLowerCase(Locale.ROOT)))
            throw AppException.badRequest("排序方向必须是 asc 或 desc");
        QuestionQueryDTO q = new QuestionQueryDTO();
        q.setOwnerId(ownerId);
        q.setCategoryId(categoryId);
        q.setDifficulty(difficulty != null ? difficulty.toLowerCase(Locale.ROOT) : null);
        q.setQuestionType(questionType != null ? questionType.toLowerCase(Locale.ROOT) : null);
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
        validatePage(page, size);
        Long ownerId = "teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null;
        QuestionQueryDTO query = buildQuery(ownerId, categoryId, difficulty, questionType, keyword, tagId, sortBy, sortOrder);
        Page<Question> pg = new Page<>(page, size);
        questionMapper.selectQuestionPage(pg, query);
        Map<Long, Category> catMap = loadAllCategories();
        Map<Long, List<Tag>> tagsByQuestion = loadTagsForQuestions(
                pg.getRecords().stream().map(Question::getId).toList());
        List<Map<String, Object>> items = pg.getRecords().stream().map(q -> {
            List<Tag> tags = tagsByQuestion.getOrDefault(q.getId(), List.of());
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
        Page<Question> pg = new Page<>(1, ExcelService.MAX_EXPORT_ROWS + 1L);
        questionMapper.selectQuestionPage(pg, query);
        if (pg.getTotal() > ExcelService.MAX_EXPORT_ROWS || pg.getRecords().size() > ExcelService.MAX_EXPORT_ROWS)
            throw AppException.badRequest("导出结果超过 " + ExcelService.MAX_EXPORT_ROWS + " 条，请增加筛选条件");
        Map<Long, Category> catMap = loadAllCategories();
        List<String> headers = List.of("id", "title", "question_type", "difficulty", "options", "answer",
                "explanation", "category_path", "tag_names", "usage_count", "created_at", "updated_at");
        Map<Long, List<Tag>> tagsByQuestion = loadTagsForQuestions(
                pg.getRecords().stream().map(Question::getId).toList());
        List<Map<String, Object>> rows = pg.getRecords().stream().map(q -> {
            List<Tag> tags = tagsByQuestion.getOrDefault(q.getId(), List.of());
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
        String normalizedFormat = excelService.normalizeFormat(format);
        byte[] bytes = excelService.export(normalizedFormat, headers, rows);
        MediaType contentType = "csv".equals(normalizedFormat)
                ? MediaType.parseMediaType("text/csv;charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questions_export_" + ts + "." + normalizedFormat + "\"")
                .contentType(contentType)
                .body(bytes);
    }

    @PostMapping("/import")
    @Transactional
    public Map<String, Object> importQuestions(@RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        lockCurrentManager(loginUser);
        if (file == null || file.isEmpty()) throw AppException.badRequest("请选择要导入的文件");
        List<Map<String, String>> rows = excelService.parse(file.getOriginalFilename(), file.getBytes());
        if (rows.isEmpty()) return Map.of("total", 0, "created", 0, "updated", 0, "errors", List.of());

        // Lock all existing imported questions in ID order so a bulk import
        // cannot deadlock with exam creation that locks the same questions.
        Set<Long> importedIds = new TreeSet<>();
        for (Map<String, String> row : rows) {
            String rawId = coalesce(row, "id", null);
            try {
                if (rawId != null && !rawId.isBlank()) importedIds.add(Long.parseLong(rawId.strip()));
            } catch (NumberFormatException ignored) { }
        }
        Map<Long, Question> lockedExistingQuestions = new HashMap<>();
        for (Long importedId : importedIds) {
            Question locked = lockQuestion(importedId);
            if (locked != null) lockedExistingQuestions.put(importedId, locked);
        }

        Map<String, Tag> tagCache = new HashMap<>();
        Map<String, Category> catCache = new HashMap<>();
        refreshImportCaches(tagCache, catCache, loginUser);
        TransactionStatus transaction = TransactionAspectSupport.currentTransactionStatus();
        int created = 0, updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNum = i + 2;
            Object savepoint = transaction.createSavepoint();
            try {
                String qType = coalesce(row, "question_type", "type", "题型").strip().toLowerCase();
                if (qType.isEmpty()) qType = "single";
                if (!QUESTION_TYPES.contains(qType)) throw new IllegalArgumentException("question_type must be single/multiple/truefalse/essay");
                String diff = coalesce(row, "difficulty", "level", "难度").strip().toLowerCase();
                if (diff.isEmpty()) diff = "medium";
                if (!DIFFICULTY_LEVELS.contains(diff)) throw new IllegalArgumentException("difficulty must be easy/medium/hard");
                String title = coalesce(row, "title", "question", "题目", "").strip();
                if (title.isEmpty()) throw new IllegalArgumentException("title is required");
                String rawAnswer = coalesce(row, "answer", "solution", "答案", "").strip();
                if (rawAnswer.isEmpty()) throw new IllegalArgumentException("answer is required");
                String answer = normalizeAnswer(rawAnswer, qType);
                String options = normalizeOptions(coalesce(row, "options", "choices", "选项", null), qType);
                String explanation = coalesce(row, "explanation", "analysis", "解析", null);
                explanation = explanation != null && explanation.isEmpty() ? null : explanation;

                // Validate and normalize all question content before creating
                // any referenced category/tag records.
                Question candidate = new Question();
                candidate.setTitle(title); candidate.setQuestionType(qType); candidate.setDifficulty(diff);
                candidate.setOptions(options); candidate.setAnswer(answer); candidate.setExplanation(explanation);
                validateAndNormalizeQuestion(candidate, loginUser);

                String catPath = coalesce(row, "category_path", "category_name", "category", "分类", null);
                Long categoryId = resolveCategoryPath(catPath, catCache, loginUser);
                List<Long> tagIds = resolveTagNames(coalesce(row, "tag_names", "tags", "labels", "标签", null), tagCache, loginUser);

                String rawId = coalesce(row, "id", null);
                Long questionId = null;
                try {
                    if (rawId != null && !rawId.isBlank()) questionId = Long.parseLong(rawId.strip());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("id 必须是正整数");
                }
                if (questionId != null && questionId <= 0) throw new IllegalArgumentException("id 必须是正整数");

                Question q = questionId != null ? lockedExistingQuestions.get(questionId) : null;
                if (questionId != null && q == null) throw new IllegalArgumentException("指定的题目不存在");
                if (questionId != null && q != null && "teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
                    throw new IllegalArgumentException("teachers can only update their own questions");

                boolean isNew = q == null;
                if (!isNew && hasExamReferences(q.getId()))
                    throw new IllegalArgumentException("已被考试引用的试题不可修改");
                if (isNew) { q = new Question(); q.setCreatorId(loginUser.getUserId()); q.setUsageCount(0); }
                q.setTitle(candidate.getTitle()); q.setQuestionType(candidate.getQuestionType());
                q.setDifficulty(candidate.getDifficulty()); q.setOptions(candidate.getOptions());
                q.setAnswer(candidate.getAnswer()); q.setExplanation(candidate.getExplanation());
                q.setCategoryId(categoryId);
                validateCategory(categoryId, loginUser);
                validateTags(tagIds, loginUser);
                if (isNew) questionMapper.insert(q);
                else {
                    questionMapper.updateById(q);
                    questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                            .eq(Question::getId, q.getId())
                            .set(Question::getOptions, q.getOptions())
                            .set(Question::getExplanation, q.getExplanation())
                            .set(Question::getCategoryId, q.getCategoryId()));
                }
                questionTagMapper.deleteByQuestionId(q.getId());
                for (Long tid : tagIds) questionTagMapper.insert(q.getId(), tid);
                if (isNew) created++; else updated++;
                transaction.releaseSavepoint(savepoint);
            } catch (RuntimeException ex) {
                transaction.rollbackToSavepoint(savepoint);
                transaction.releaseSavepoint(savepoint);
                refreshImportCaches(tagCache, catCache, loginUser);
                refreshLockedQuestions(importedIds, lockedExistingQuestions);
                String message = ex instanceof DataIntegrityViolationException
                        ? "名称或关联数据与现有记录冲突"
                        : (ex.getMessage() != null ? ex.getMessage() : "导入失败");
                errors.add(Map.of("row", lineNum, "message", message));
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
    @Transactional
    public Map<String, Object> createQuestion(@Valid @RequestBody QuestionCreateDTO data,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        lockCurrentManager(loginUser);
        Question q = new Question();
        q.setTitle(data.getTitle()); q.setQuestionType(data.getQuestionType());
        q.setDifficulty(data.getDifficulty()); q.setOptions(data.getOptions());
        q.setAnswer(data.getAnswer()); q.setExplanation(data.getExplanation());
        q.setCategoryId(data.getCategoryId());
        q.setCreatorId(loginUser.getUserId()); q.setUsageCount(0);
        validateAndNormalizeQuestion(q, loginUser);
        List<Long> tagIds = normalizeTagIds(data.getTagIds());
        validateTags(tagIds, loginUser);
        questionMapper.insert(q);
        for (Long tagId : tagIds) questionTagMapper.insert(q.getId(), tagId);
        return questionToMap(q, loadAllCategories(), loadTagsForQuestion(q.getId()));
    }

    @PutMapping("/{questionId}")
    @Transactional
    public Map<String, Object> updateQuestion(@PathVariable Long questionId,
                                               @Valid @RequestBody QuestionCreateDTO data,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Question q = lockQuestion(questionId);
        if (q == null) throw AppException.notFound("Question not found");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
            throw AppException.notFound("Question not found");
        if (hasExamReferences(questionId)) throw AppException.badRequest("已被考试引用的试题不可修改");
        if (data.getTitle() != null) q.setTitle(data.getTitle());
        if (data.getQuestionType() != null) q.setQuestionType(data.getQuestionType());
        if (data.getDifficulty() != null) q.setDifficulty(data.getDifficulty());
        if (data.isOptionsPresent()) q.setOptions(data.getOptions());
        if (data.getAnswer() != null) q.setAnswer(data.getAnswer());
        if (data.isExplanationPresent()) q.setExplanation(data.getExplanation());
        if (data.isCategoryIdPresent()) q.setCategoryId(data.getCategoryId());
        validateAndNormalizeQuestion(q, loginUser);
        questionMapper.updateById(q);
        // MyBatis-Plus skips null entity fields by default. Apply nullable
        // fields explicitly so a requested clear is persisted, not merely
        // reflected in this response object.
        boolean optionsMustBeWritten = data.isOptionsPresent()
                || !Set.of("single", "multiple").contains(q.getQuestionType());
        if (optionsMustBeWritten || data.isExplanationPresent() || data.isCategoryIdPresent()) {
            questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                    .eq(Question::getId, questionId)
                    .set(optionsMustBeWritten, Question::getOptions, q.getOptions())
                    .set(data.isExplanationPresent(), Question::getExplanation, q.getExplanation())
                    .set(data.isCategoryIdPresent(), Question::getCategoryId, q.getCategoryId()));
        }
        if (data.isTagIdsPresent()) {
            List<Long> tagIds = normalizeTagIds(data.getTagIds());
            validateTags(tagIds, loginUser);
            questionTagMapper.deleteByQuestionId(questionId);
            for (Long tagId : tagIds) questionTagMapper.insert(questionId, tagId);
        }
        return questionToMap(q, loadAllCategories(), loadTagsForQuestion(questionId));
    }

    @DeleteMapping("/{questionId}")
    @Transactional
    public Map<String, Boolean> deleteQuestion(@PathVariable Long questionId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Question q = lockQuestion(questionId);
        if (q == null) throw AppException.notFound("Question not found");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(q.getCreatorId()))
            throw AppException.notFound("Question not found");
        if (hasExamReferences(questionId)) throw AppException.badRequest("已被考试引用的试题不可删除");
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

    private void validatePage(int page, int size) {
        if (page < 1) throw AppException.badRequest("页码必须大于等于1");
        if (size < 1 || size > 100) throw AppException.badRequest("每页数量必须在1到100之间");
    }

    private boolean canUseOwnedResource(Long creatorId, LoginUser loginUser) {
        return "admin".equals(loginUser.getRole()) || creatorId == null
                || loginUser.getUserId().equals(creatorId);
    }

    private void validateAndNormalizeQuestion(Question question, LoginUser loginUser) {
        String title = question.getTitle() == null ? "" : question.getTitle().strip();
        if (title.isEmpty()) throw AppException.badRequest("题目内容不能为空");
        if (title.length() > 5000) throw AppException.badRequest("题目内容不能超过5000个字符");
        question.setTitle(title);

        String type = question.getQuestionType() == null ? "" : question.getQuestionType().strip().toLowerCase();
        if (!QUESTION_TYPES.contains(type)) throw AppException.badRequest("题型必须是 single、multiple、truefalse 或 essay");
        question.setQuestionType(type);
        String difficulty = question.getDifficulty() == null ? "" : question.getDifficulty().strip().toLowerCase();
        if (!DIFFICULTY_LEVELS.contains(difficulty)) throw AppException.badRequest("难度必须是 easy、medium 或 hard");
        question.setDifficulty(difficulty);

        String answer = question.getAnswer() == null ? "" : question.getAnswer().strip();
        if (answer.isEmpty()) throw AppException.badRequest("答案不能为空");
        if (answer.length() > 10000) throw AppException.badRequest("答案不能超过10000个字符");

        if (Set.of("single", "multiple").contains(type)) {
            List<String> options = parseAndNormalizeOptions(question.getOptions());
            question.setOptions(writeOptions(options));
            question.setAnswer(normalizeChoiceAnswer(answer, type, options.size()));
        } else if ("truefalse".equals(type)) {
            question.setOptions(null);
            try {
                question.setAnswer(normalizeAnswer(answer, type));
            } catch (IllegalArgumentException ex) {
                throw AppException.badRequest(ex.getMessage());
            }
        } else {
            question.setOptions(null);
            question.setAnswer(answer);
        }
        if (question.getExplanation() != null) {
            String explanation = question.getExplanation().strip();
            if (explanation.length() > 20000) throw AppException.badRequest("解析不能超过20000个字符");
            question.setExplanation(explanation.isEmpty() ? null : explanation);
        }
        validateCategory(question.getCategoryId(), loginUser);
    }

    private List<String> parseAndNormalizeOptions(String raw) {
        if (raw == null || raw.isBlank()) throw AppException.badRequest("单选题和多选题至少需要两个选项");
        if (raw.length() > 20000) throw AppException.badRequest("选项内容不能超过20000个字符");
        final List<String> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw AppException.badRequest("选项必须是字符串数组格式的 JSON");
        }
        if (parsed == null) throw AppException.badRequest("选项必须是字符串数组格式的 JSON");
        if (parsed.size() < 2 || parsed.size() > 26) throw AppException.badRequest("选项数量必须在2到26之间");
        List<String> normalized = new ArrayList<>(parsed.size());
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < parsed.size(); i++) {
            String value = parsed.get(i);
            if (value == null) throw AppException.badRequest("选项不能为空");
            value = value.strip().replaceFirst("^[A-Za-z]\\.\\s*", "").strip();
            if (value.isEmpty()) throw AppException.badRequest("选项不能为空");
            if (value.length() > 2000) throw AppException.badRequest("单个选项不能超过2000个字符");
            if (!unique.add(value.toLowerCase(Locale.ROOT))) throw AppException.badRequest("选项内容不能重复");
            normalized.add((char) ('A' + i) + ". " + value);
        }
        return normalized;
    }

    private String writeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化题目选项", ex);
        }
    }

    private String normalizeChoiceAnswer(String raw, String type, int optionCount) {
        List<String> choices = Arrays.stream(raw.toUpperCase(Locale.ROOT).split(","))
                .map(String::strip).filter(v -> !v.isEmpty()).toList();
        if (choices.isEmpty()) throw AppException.badRequest("答案不能为空");
        if ("single".equals(type) && choices.size() != 1) throw AppException.badRequest("单选题只能有一个正确答案");
        Set<String> unique = new TreeSet<>();
        for (String choice : choices) {
            if (!choice.matches("^[A-Z]$")) throw AppException.badRequest("选择题答案必须使用选项字母");
            int index = choice.charAt(0) - 'A';
            if (index < 0 || index >= optionCount) throw AppException.badRequest("答案引用了不存在的选项");
            if (!unique.add(choice)) throw AppException.badRequest("答案中不能包含重复选项");
        }
        return String.join(",", unique);
    }

    private void validateCategory(Long categoryId, LoginUser loginUser) {
        if (categoryId == null) return;
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) throw AppException.badRequest("分类不存在");
        if (!canUseOwnedResource(category.getCreatorId(), loginUser))
            throw AppException.forbidden("不能使用其他教师创建的分类");
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null) return List.of();
        if (tagIds.size() > 100) throw AppException.badRequest("单道题最多关联100个标签");
        if (tagIds.stream().anyMatch(Objects::isNull)) throw AppException.badRequest("标签ID不能为空");
        return tagIds.stream().distinct().toList();
    }

    private void validateTags(List<Long> tagIds, LoginUser loginUser) {
        if (tagIds.isEmpty()) return;
        // Hold shared locks until the surrounding question transaction has
        // written its join rows. Tag deletion takes an exclusive lock first,
        // so it cannot pass its ownership/usage check and then erase a newly
        // created association from another teacher.
        List<Long> sortedTagIds = tagIds.stream().sorted().toList();
        Map<Long, Tag> existing = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .in(Tag::getId, sortedTagIds)
                        .orderByAsc(Tag::getId)
                        .last("FOR SHARE"))
                .stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));
        for (Long tagId : sortedTagIds) {
            Tag tag = existing.get(tagId);
            if (tag == null) throw AppException.badRequest("标签不存在: " + tagId);
        }
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
        if (parts.length > 20) throw new IllegalArgumentException("分类层级不能超过20级");
        for (String part : parts) {
            String name = part.strip();
            if (!name.isEmpty() && name.length() > 128)
                throw new IllegalArgumentException("分类名称不能超过128个字符");
        }
        Long parentId = null;
        Category current = null;
        for (String part : parts) {
            String name = part.strip();
            if (name.isEmpty()) continue;
            String key = (parentId == null ? "null" : parentId) + ":" + name.toLowerCase();
            current = cache.get(key);
            if (current == null) {
                // Lock and re-read the sibling range just before inserting.
                // Import requests otherwise bypassed CategoryController's
                // duplicate-name protection and could create two identical
                // paths concurrently from stale in-memory caches.
                List<Category> siblings = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(parentId != null, Category::getParentId, parentId)
                        .isNull(parentId == null, Category::getParentId)
                        .orderByAsc(Category::getId).last("FOR UPDATE"));
                List<Category> equivalentNames = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(parentId != null, Category::getParentId, parentId)
                        .isNull(parentId == null, Category::getParentId)
                        .eq(Category::getName, name)
                        .last("FOR UPDATE"));
                Category existing = equivalentNames.isEmpty() ? null : equivalentNames.get(0);
                if (existing != null) {
                    if (!canUseOwnedResource(existing.getCreatorId(), loginUser))
                        throw AppException.conflict("同级分类名称已由其他教师使用: " + name);
                    current = existing;
                } else {
                    Category c = new Category();
                    c.setName(name); c.setParentId(parentId);
                    c.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
                    c.setSortOrder(siblings.size());
                    categoryMapper.insert(c);
                    current = c;
                }
                cache.put(key, current);
            }
            parentId = current.getId();
        }
        return current != null ? current.getId() : null;
    }

    private List<Long> resolveTagNames(String raw, Map<String, Tag> cache, LoginUser loginUser) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> names = Arrays.stream(raw.split("[,|/]+"))
                .map(String::strip).filter(name -> !name.isEmpty()).distinct().toList();
        if (names.size() > 100) throw new IllegalArgumentException("单道题最多关联100个标签");
        if (names.stream().anyMatch(name -> name.length() > 64))
            throw new IllegalArgumentException("标签名称不能超过64个字符");
        List<Long> ids = new ArrayList<>();
        for (String n : names) {
            Tag t = cache.get(n.toLowerCase());
            if (t == null) {
                t = new Tag(); t.setName(n); t.setColor("#409EFF");
                t.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
                try {
                    tagMapper.insert(t);
                } catch (DataIntegrityViolationException duplicate) {
                    // Another importer may have committed the same globally
                    // unique tag after our cache was loaded. A locking current
                    // read sees that committed row even under REPEATABLE READ.
                    List<Tag> existing = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                            .eq(Tag::getName, n).orderByAsc(Tag::getId).last("FOR UPDATE"));
                    if (existing.isEmpty()) throw duplicate;
                    t = existing.get(0);
                }
                cache.put(n.toLowerCase(), t);
            }
            ids.add(t.getId());
        }
        return ids;
    }

    private void refreshImportCaches(Map<String, Tag> tagCache,
                                     Map<String, Category> categoryCache,
                                     LoginUser loginUser) {
        tagCache.clear();
        tagMapper.selectList(null).forEach(tag -> tagCache.put(tag.getName().toLowerCase(), tag));
        categoryCache.clear();
        categoryMapper.selectList(null).stream()
                .filter(category -> canUseOwnedResource(category.getCreatorId(), loginUser))
                .forEach(category -> {
                    String key = (category.getParentId() == null ? "null" : category.getParentId())
                            + ":" + category.getName().toLowerCase();
                    categoryCache.put(key, category);
                });
    }

    private void refreshLockedQuestions(Set<Long> importedIds,
                                        Map<Long, Question> lockedQuestions) {
        lockedQuestions.clear();
        for (Long importedId : importedIds.stream().sorted().toList()) {
            Question locked = lockQuestion(importedId);
            if (locked != null) lockedQuestions.put(importedId, locked);
        }
    }
}
