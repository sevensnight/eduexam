package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String type;
    private Boolean isRead;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
