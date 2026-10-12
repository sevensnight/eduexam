package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.Category;
import com.eduexam.domain.Question;
import com.eduexam.domain.User;
import com.eduexam.dto.CategoryReorderItemDTO;
import com.eduexam.mapper.CategoryMapper;
import com.eduexam.mapper.QuestionMapper;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;

    @Data
    static class CategoryCreateRequest {
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 128, message = "分类名称不能超过128个字符")
        private String name;
        @Size(max = 20000, message = "分类描述不能超过20000个字符")
        private String description;
        private Long parentId;
    }

    @Data
    static class CategoryUpdateRequest {
        @Size(max = 128, message = "分类名称不能超过128个字符")
        private String name;
        @Size(max = 20000, message = "分类描述不能超过20000个字符")
        private String description;
        @JsonIgnore
        private boolean descriptionPresent;
        private Long parentId;
        @JsonIgnore
        private boolean parentIdPresent;

        @JsonSetter("description")
        public void setDescription(String description) {
            this.description = description;
            this.descriptionPresent = true;
        }

        @JsonSetter("parent_id")
        public void setParentId(Long parentId) {
            this.parentId = parentId;
            this.parentIdPresent = true;
        }
    }

    private void requireCategoryRole(LoginUser loginUser) {
        if (loginUser == null || (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole())))
            throw AppException.forbidden("无权访问");
    }

    private void lockCurrentManager(LoginUser loginUser) {
        User current = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, loginUser.getUserId()).last("FOR SHARE"));
        if (current == null || !Boolean.TRUE.equals(current.getIsActive())
                || !Set.of("admin", "teacher").contains(current.getRole()))
            throw AppException.forbidden("账号状态或角色已发生变化，请重新登录");
    }

    private void requireManagePermission(Category category, LoginUser loginUser) {
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(category.getCreatorId()))
            throw AppException.forbidden("只能管理自己创建的分类");
    }

    private boolean canUseAsParent(Category category, LoginUser loginUser) {
        return "admin".equals(loginUser.getRole()) || category.getCreatorId() == null
                || loginUser.getUserId().equals(category.getCreatorId());
    }

    private Map<String, Object> catToMap(Category c, int questionCount, List<Map<String, Object>> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("parent_id", c.getParentId());
        m.put("creator_id", c.getCreatorId());
        m.put("sort_order", c.getSortOrder());
        m.put("question_count", questionCount);
        m.put("children", children != null ? children : List.of());
        return m;
    }

    private List<Map<String, Object>> buildTree(List<Category> all, Map<Long, Integer> counts,
                                                 Long parentId, Set<Long> visibleIds, Set<Long> ancestors) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category c : all) {
            Long cParent = c.getParentId();
            boolean matches = Objects.equals(parentId, cParent);
            if (!matches || (visibleIds != null && !visibleIds.contains(c.getId()))) continue;
            if (!ancestors.add(c.getId())) continue;
            List<Map<String, Object>> children = buildTree(all, counts, c.getId(), visibleIds, ancestors);
            ancestors.remove(c.getId());
            result.add(catToMap(c, counts.getOrDefault(c.getId(), 0), children));
        }
        return result;
    }

    @GetMapping
    public List<Map<String, Object>> listCategories(@AuthenticationPrincipal LoginUser loginUser) {
        requireCategoryRole(loginUser);
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));

        LambdaQueryWrapper<Question> questions = new LambdaQueryWrapper<Question>()
                .select(Question::getCategoryId).isNotNull(Question::getCategoryId);
        if ("teacher".equals(loginUser.getRole())) questions.eq(Question::getCreatorId, loginUser.getUserId());
        Map<Long, Integer> counts = new HashMap<>();
        questionMapper.selectList(questions).forEach(q -> counts.merge(q.getCategoryId(), 1, Integer::sum));

        Set<Long> visibleIds = "teacher".equals(loginUser.getRole()) ? computeVisibleIds(all, loginUser) : null;
        return buildTree(all, counts, null, visibleIds, new HashSet<>());
    }

    private Set<Long> computeVisibleIds(List<Category> all, LoginUser loginUser) {
        Set<Long> visible = new HashSet<>();
        // Global categories are available to every teacher; their descendants are
        // still filtered unless owned or used by that teacher.
        all.stream().filter(c -> c.getCreatorId() == null).forEach(c -> visible.add(c.getId()));
        questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getCategoryId)
                        .eq(Question::getCreatorId, loginUser.getUserId())
                        .isNotNull(Question::getCategoryId))
                .forEach(q -> visible.add(q.getCategoryId()));
        all.stream().filter(c -> loginUser.getUserId().equals(c.getCreatorId()))
                .forEach(c -> visible.add(c.getId()));
        Map<Long, Category> catMap = all.stream().collect(Collectors.toMap(Category::getId, c -> c));
        for (Long id : new HashSet<>(visible)) {
            Category cur = catMap.get(id);
            Set<Long> visited = new HashSet<>();
            while (cur != null && cur.getParentId() != null && visited.add(cur.getId())) {
                visible.add(cur.getParentId());
                cur = catMap.get(cur.getParentId());
            }
        }
        return visible;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> createCategory(@Valid @RequestBody CategoryCreateRequest req,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        requireCategoryRole(loginUser);
        lockCurrentManager(loginUser);
        String name = req.getName().strip();
        if (req.getParentId() != null) {
            Category parent = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getId, req.getParentId()).last("FOR UPDATE"));
            if (parent == null) throw AppException.badRequest("父分类不存在");
            if (!canUseAsParent(parent, loginUser)) throw AppException.forbidden("不能使用其他教师的分类作为父分类");
        }
        List<Category> siblings = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(req.getParentId() != null, Category::getParentId, req.getParentId())
                .isNull(req.getParentId() == null, Category::getParentId)
                .orderByAsc(Category::getId).last("FOR UPDATE"));
        List<Category> equivalentNames = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(req.getParentId() != null, Category::getParentId, req.getParentId())
                .isNull(req.getParentId() == null, Category::getParentId)
                // Let MySQL apply the column's collation. Java's
                // equalsIgnoreCase does not cover all accent/collation pairs.
                .eq(Category::getName, name)
                .last("FOR UPDATE"));
        if (!equivalentNames.isEmpty())
            throw AppException.conflict("同级分类名称已存在");
        Category c = new Category();
        c.setName(name);
        c.setDescription(trimToNull(req.getDescription()));
        c.setParentId(req.getParentId());
        c.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
        c.setSortOrder(siblings.size());
        categoryMapper.insert(c);
        return catToMap(c, 0, List.of());
    }

    @PutMapping("/reorder")
    @Transactional
    public Map<String, Boolean> reorderCategories(@Valid @RequestBody List<@Valid CategoryReorderItemDTO> items,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        requireCategoryRole(loginUser);
        if (items == null || items.isEmpty()) throw AppException.badRequest("排序列表不能为空");
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getId).last("FOR UPDATE"));
        Map<Long, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, c -> c));
        Map<Long, Long> effectiveParents = parentMap(all);
        Set<Long> seen = new HashSet<>();

        for (CategoryReorderItemDTO item : items) {
            if (!seen.add(item.getId())) throw AppException.badRequest("排序列表包含重复分类");
            Category category = byId.get(item.getId());
            if (category == null) throw AppException.notFound("分类不存在: " + item.getId());
            requireManagePermission(category, loginUser);
            if (item.getParentId() != null) {
                Category parent = byId.get(item.getParentId());
                if (parent == null) throw AppException.badRequest("父分类不存在: " + item.getParentId());
                if (!canUseAsParent(parent, loginUser)) throw AppException.forbidden("不能使用其他教师的分类作为父分类");
            }
            effectiveParents.put(item.getId(), item.getParentId());
        }
        validateAcyclic(effectiveParents);
        for (CategoryReorderItemDTO item : items) {
            Category category = byId.get(item.getId());
            category.setParentId(item.getParentId());
            category.setSortOrder(item.getSortOrder());
            categoryMapper.updateById(category);
            categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                    .eq(Category::getId, category.getId())
                    .set(Category::getParentId, category.getParentId()));
        }
        // Validate the hypothetical hierarchy after writing it inside this
        // transaction, so MySQL's actual column collation decides equality.
        // Throwing rolls every move back atomically.
        ensureNoSiblingNameDuplicates();
        return Map.of("ok", true);
    }

    @PutMapping("/{catId}")
    @Transactional
    public Map<String, Object> updateCategory(@PathVariable Long catId,
                                               @Valid @RequestBody CategoryUpdateRequest req,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        requireCategoryRole(loginUser);
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getId).last("FOR UPDATE"));
        Map<Long, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, c -> c));
        Category c = byId.get(catId);
        if (c == null) throw AppException.notFound("分类不存在");
        requireManagePermission(c, loginUser);

        if (req.getName() != null) {
            String name = req.getName().strip();
            if (name.isEmpty()) throw AppException.badRequest("分类名称不能为空");
            ensureSiblingNameAvailable(catId, req.isParentIdPresent() ? req.getParentId() : c.getParentId(), name);
            c.setName(name);
        }
        if (req.isDescriptionPresent()) c.setDescription(trimToNull(req.getDescription()));
        if (req.isParentIdPresent()) {
            if (req.getParentId() != null) {
                Category parent = byId.get(req.getParentId());
                if (parent == null) throw AppException.badRequest("父分类不存在");
                if (!canUseAsParent(parent, loginUser)) throw AppException.forbidden("不能使用其他教师的分类作为父分类");
            }
            Map<Long, Long> effectiveParents = parentMap(all);
            effectiveParents.put(catId, req.getParentId());
            validateAcyclic(effectiveParents);
            c.setParentId(req.getParentId());
        }
        if (req.getName() == null && req.isParentIdPresent()) {
            ensureSiblingNameAvailable(catId, req.getParentId(), c.getName());
        }
        categoryMapper.updateById(c);
        if (req.isDescriptionPresent() || req.isParentIdPresent()) {
            categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                    .eq(Category::getId, catId)
                    .set(req.isDescriptionPresent(), Category::getDescription, c.getDescription())
                    .set(req.isParentIdPresent(), Category::getParentId, c.getParentId()));
        }
        return catToMap(c, 0, List.of());
    }

    @DeleteMapping("/{catId}")
    @Transactional
    public Map<String, Boolean> deleteCategory(@PathVariable Long catId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        requireCategoryRole(loginUser);
        Category c = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, catId).last("FOR UPDATE"));
        if (c == null) throw AppException.notFound("分类不存在");
        requireManagePermission(c, loginUser);
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, catId)) > 0)
            throw AppException.conflict("该分类仍有子分类，请先移动或删除子分类");
        if (questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getCategoryId, catId)) > 0)
            throw AppException.conflict("该分类仍被题目使用，请先移动题目");
        categoryMapper.deleteById(catId);
        return Map.of("ok", true);
    }

    private void validateAcyclic(Map<Long, Long> parents) {
        for (Long start : parents.keySet()) {
            Set<Long> path = new HashSet<>();
            Long current = start;
            while (current != null) {
                if (!path.add(current)) throw AppException.badRequest("分类层级不能形成循环");
                Long next = parents.get(current);
                if (next != null && !parents.containsKey(next)) throw AppException.badRequest("父分类不存在");
                current = next;
            }
        }
    }

    private Map<Long, Long> parentMap(List<Category> categories) {
        Map<Long, Long> result = new HashMap<>();
        for (Category category : categories) result.put(category.getId(), category.getParentId());
        return result;
    }

    private void ensureSiblingNameAvailable(Long currentId, Long parentId, String name) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .eq(parentId != null, Category::getParentId, parentId)
                .isNull(parentId == null, Category::getParentId)
                .ne(currentId != null, Category::getId, currentId);
        if (categoryMapper.selectCount(wrapper) > 0) throw AppException.conflict("同级分类名称已存在");
    }

    void ensureNoSiblingNameDuplicates() {
        if (!categoryMapper.selectMaps(new QueryWrapper<Category>()
                .select("parent_id", "name", "COUNT(*) AS duplicate_count")
                .groupBy("parent_id", "name")
                .having("COUNT(*) > 1")
                .last("LIMIT 1")).isEmpty()) {
            throw AppException.conflict("移动后会产生同级同名分类");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
