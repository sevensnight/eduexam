package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("courses")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long teacherId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
