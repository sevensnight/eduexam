package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.Category;
import com.eduexam.domain.Question;
import com.eduexam.dto.CategoryReorderItemDTO;
import com.eduexam.mapper.CategoryMapper;
import com.eduexam.mapper.QuestionMapper;
import com.eduexam.security.LoginUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;

    @Data
    static class CategoryCreateRequest {
        private String name;
        private String description;
        private Long parentId;
    }

    @Data
    static class CategoryUpdateRequest {
        private String name;
        private String description;
        private Long parentId;
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
                                                 Long parentId, LoginUser loginUser, Set<Long> visibleIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category c : all) {
            Long cParent = c.getParentId();
            boolean matches = (parentId == null && cParent == null) || (parentId != null && parentId.equals(cParent));
            if (!matches) continue;
            if (visibleIds != null && !visibleIds.contains(c.getId())) continue;
            List<Map<String, Object>> children = buildTree(all, counts, c.getId(), loginUser, visibleIds);
            result.add(catToMap(c, counts.getOrDefault(c.getId(), 0), children));
        }
        return result;
    }

    @GetMapping
    public List<Map<String, Object>> listCategories(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");

        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));

        LambdaQueryWrapper<Question> countWrapper = new LambdaQueryWrapper<Question>()
                .select(Question::getCategoryId)
                .isNotNull(Question::getCategoryId)
                .groupBy(Question::getCategoryId);
        if ("teacher".equals(loginUser.getRole())) countWrapper.eq(Question::getCreatorId, loginUser.getUserId());

        // Count per category
        Map<Long, Integer> counts = new HashMap<>();
        questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .isNotNull(Question::getCategoryId)
                .apply("teacher".equals(loginUser.getRole()), "creator_id = " + loginUser.getUserId()))
                .forEach(q -> counts.merge(q.getCategoryId(), 1, Integer::sum));

        Set<Long> visibleIds = null;
        if ("teacher".equals(loginUser.getRole())) {
            visibleIds = computeVisibleIds(all, loginUser);
        }

        return buildTree(all, counts, null, loginUser, visibleIds);
    }

    private Set<Long> computeVisibleIds(List<Category> all, LoginUser loginUser) {
        Set<Long> visible = new HashSet<>();
        questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getCreatorId, loginUser.getUserId())
                .isNotNull(Question::getCategoryId))
                .forEach(q -> visible.add(q.getCategoryId()));
        categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getCreatorId, loginUser.getUserId()))
                .forEach(c -> visible.add(c.getId()));
        Map<Long, Category> catMap = all.stream().collect(Collectors.toMap(Category::getId, c -> c));
        for (Long id : new HashSet<>(visible)) {
            Category cur = catMap.get(id);
            while (cur != null && cur.getParentId() != null) {
                visible.add(cur.getParentId());
                cur = catMap.get(cur.getParentId());
            }
        }
        return visible;
    }

    @PostMapping
    public Map<String, Object> createCategory(@RequestBody CategoryCreateRequest req,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        int sortOrder = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(req.getParentId() != null, Category::getParentId, req.getParentId())
                        .isNull(req.getParentId() == null, Category::getParentId)).intValue();
        Category c = new Category();
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c.setParentId(req.getParentId());
        c.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
        c.setSortOrder(sortOrder);
        categoryMapper.insert(c);
        return catToMap(c, 0, List.of());
    }

    @PutMapping("/reorder")
    public Map<String, Boolean> reorderCategories(@RequestBody List<CategoryReorderItemDTO> items,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        for (CategoryReorderItemDTO item : items) {
            Category c = categoryMapper.selectById(item.getId());
            if (c == null) continue;
            if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getCreatorId()))
                throw AppException.forbidden("只能管理自己创建的分类");
            c.setParentId(item.getParentId());
            c.setSortOrder(item.getSortOrder());
            categoryMapper.updateById(c);
        }
        return Map.of("ok", true);
    }

    @PutMapping("/{catId}")
    public Map<String, Object> updateCategory(@PathVariable Long catId,
                                               @RequestBody CategoryUpdateRequest req,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Category c = categoryMapper.selectById(catId);
        if (c == null) throw AppException.notFound("分类不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getCreatorId()))
            throw AppException.forbidden("只能管理自己创建的分类");
        if (req.getName() != null) c.setName(req.getName());
        if (req.getDescription() != null) c.setDescription(req.getDescription());
        if (req.getParentId() != null) c.setParentId(req.getParentId());
        categoryMapper.updateById(c);
        return catToMap(c, 0, List.of());
    }

    @DeleteMapping("/{catId}")
    public Map<String, Boolean> deleteCategory(@PathVariable Long catId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Category c = categoryMapper.selectById(catId);
        if (c == null) throw AppException.notFound("分类不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getCreatorId()))
            throw AppException.forbidden("只能管理自己创建的分类");
        categoryMapper.deleteById(catId);
        return Map.of("ok", true);
    }
}
