package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.domain.Notification;
import com.eduexam.mapper.NotificationMapper;
import com.eduexam.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationMapper notificationMapper;

    private Map<String, Object> notifToMap(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("content", n.getContent());
        m.put("type", n.getType());
        m.put("is_read", n.getIsRead());
        m.put("created_at", n.getCreatedAt());
        return m;
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, loginUser.getUserId())
                .eq(Notification::getIsRead, false));
        return Map.of("count", count);
    }

    @GetMapping
    public List<Map<String, Object>> listNotifications(@AuthenticationPrincipal LoginUser loginUser) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, loginUser.getUserId())
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT 30"))
                .stream().map(this::notifToMap).collect(Collectors.toList());
    }

    @PutMapping("/read-all")
    public Map<String, Boolean> readAll(@AuthenticationPrincipal LoginUser loginUser) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, loginUser.getUserId())
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true));
        return Map.of("ok", true);
    }
}
