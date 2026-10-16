package com.eduexam.controller;

import com.eduexam.common.AppException;
import com.eduexam.domain.Notification;
import com.eduexam.domain.User;
import com.eduexam.mapper.NotificationMapper;
import com.eduexam.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationMapper notificationMapper;

    private NotificationController controller;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationMapper);
        User user = new User();
        user.setId(7L);
        user.setUsername("student1");
        user.setRole("student");
        user.setIsActive(true);
        loginUser = new LoginUser(user);
    }

    @Test
    void listIncludesSafeBusinessTargetWithoutMarkingNotificationsRead() {
        Notification notification = notification(21L, 7L, false);
        when(notificationMapper.selectList(any())).thenReturn(List.of(notification));

        List<Map<String, Object>> result = controller.listNotifications(loginUser);

        assertEquals(1, result.size());
        assertEquals("/exams/5/result", result.get(0).get("target_path"));
        assertEquals(false, result.get(0).get("is_read"));
        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    void readOneMarksOwnedNotificationOnly() {
        Notification notification = notification(21L, 7L, false);
        when(notificationMapper.selectOne(any())).thenReturn(notification);

        Map<String, Object> result = controller.readOne(21L, loginUser);

        ArgumentCaptor<Notification> updated = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).updateById(updated.capture());
        assertTrue(updated.getValue().getIsRead());
        assertEquals(true, result.get("is_read"));
        assertEquals("/exams/5/result", result.get("target_path"));
    }

    @Test
    void readOneRejectsMissingOrForeignNotificationWithoutUpdatingAnything() {
        when(notificationMapper.selectOne(any())).thenReturn(null);

        AppException error = assertThrows(AppException.class,
                () -> controller.readOne(99L, loginUser));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatus());
        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    void readOneDoesNotWriteAgainWhenNotificationIsAlreadyRead() {
        Notification notification = notification(21L, 7L, true);
        when(notificationMapper.selectOne(any())).thenReturn(notification);

        Map<String, Object> result = controller.readOne(21L, loginUser);

        assertTrue((Boolean) result.get("is_read"));
        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    private Notification notification(long id, long userId, boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(userId);
        notification.setContent("成绩已更新");
        notification.setType("grade");
        notification.setTargetPath("/exams/5/result");
        notification.setIsRead(read);
        notification.setCreatedAt(LocalDateTime.of(2026, 10, 16, 12, 0));
        return notification;
    }
}
