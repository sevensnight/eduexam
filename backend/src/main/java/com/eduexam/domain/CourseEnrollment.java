package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_enrollments")
public class CourseEnrollment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long studentId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime enrolledAt;
}
