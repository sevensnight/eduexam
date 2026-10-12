package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.Tag;
import com.eduexam.domain.User;
import com.eduexam.mapper.QuestionTagMapper;
import com.eduexam.mapper.TagMapper;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagMapper tagMapper;
    private final QuestionTagMapper questionTagMapper;
    private final UserMapper userMapper;

    private void lockCurrentManager(LoginUser loginUser) {
        User current = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, loginUser.getUserId()).last("FOR SHARE"));
        if (current == null || !Boolean.TRUE.equals(current.getIsActive())
                || !Set.of("admin", "teacher").contains(current.getRole()))
            throw AppException.forbidden("账号状态或角色已发生变化，请重新登录");
    }

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
        // Tag names are globally unique, so tags are a shared taxonomy. The
        // creator controls deletion, but every teacher may discover/reuse one.
        return tagMapper.selectList(wrapper).stream().map(this::tagToMap).collect(Collectors.toList());
    }

    @PostMapping
    @Transactional
    public Map<String, Object> createTag(@RequestParam String name,
                                          @RequestParam(defaultValue = "#409EFF") String color,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        lockCurrentManager(loginUser);
        name = name == null ? "" : name.strip();
        color = color == null ? "" : color.strip();
        if (name.isEmpty()) throw AppException.badRequest("标签名称不能为空");
        if (name.length() > 64) throw AppException.badRequest("标签名称不能超过64个字符");
        if (!color.matches("^#[0-9a-fA-F]{6}$")) throw AppException.badRequest("标签颜色必须是 #RRGGBB 格式");
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
    @Transactional
    public Map<String, Boolean> deleteTag(@PathVariable Long tagId,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        lockCurrentManager(loginUser);
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getId, tagId).last("FOR UPDATE"));
        if (tag == null) throw AppException.notFound("标签不存在");
        if ("teacher".equals(loginUser.getRole())) {
            if (!loginUser.getUserId().equals(tag.getCreatorId()))
                throw AppException.forbidden("只能删除自己创建的标签");
            List<Long> otherUsage = questionTagMapper.findTagUsageByOtherTeachers(tagId, loginUser.getUserId());
            if (!otherUsage.isEmpty())
                throw AppException.badRequest("该标签仍被其他教师题目使用，不能删除");
        }
        // Delete joins explicitly as well as relying on the FK cascade. This
        // keeps legacy databases safe before the migration has run.
        questionTagMapper.deleteByTagId(tagId);
        tagMapper.deleteById(tagId);
        return Map.of("ok", true);
    }
}
