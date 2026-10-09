package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.Tag;
import com.eduexam.mapper.QuestionTagMapper;
import com.eduexam.mapper.TagMapper;
import com.eduexam.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagMapper tagMapper;
    private final QuestionTagMapper questionTagMapper;

    private Map<String, Object> tagToMap(Tag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("color", t.getColor());
        m.put("creator_id", t.getCreatorId());
        return m;
    }

    @GetMapping
    public List<Map<String, Object>> listTags(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getName);
        if ("teacher".equals(loginUser.getRole())) {
            List<Long> usedTagIds = questionTagMapper.findTagIdsByTeacher(loginUser.getUserId());
            wrapper.and(w -> w.eq(Tag::getCreatorId, loginUser.getUserId())
                    .or().in(!usedTagIds.isEmpty(), Tag::getId, usedTagIds));
        }
        return tagMapper.selectList(wrapper).stream().map(this::tagToMap).collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> createTag(@RequestParam String name,
                                          @RequestParam(defaultValue = "#409EFF") String color,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        if (tagMapper.selectCount(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name)) > 0)
            throw AppException.conflict("标签名称已存在");
        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color);
        tag.setCreatorId("teacher".equals(loginUser.getRole()) ? loginUser.getUserId() : null);
        tagMapper.insert(tag);
        return tagToMap(tag);
    }

    @DeleteMapping("/{tagId}")
    public Map<String, Boolean> deleteTag(@PathVariable Long tagId,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) throw AppException.notFound("标签不存在");
        if ("teacher".equals(loginUser.getRole())) {
            if (!loginUser.getUserId().equals(tag.getCreatorId()))
                throw AppException.forbidden("只能删除自己创建的标签");
            List<Long> otherUsage = questionTagMapper.findTagUsageByOtherTeachers(tagId, loginUser.getUserId());
            if (!otherUsage.isEmpty())
                throw AppException.badRequest("该标签仍被其他教师题目使用，不能删除");
        }
        tagMapper.deleteById(tagId);
        return Map.of("ok", true);
    }
}
