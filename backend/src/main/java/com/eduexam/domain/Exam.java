package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exams")
public class Exam {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    @TableField("creator_id")
    private Long teacherId;
    private Long courseId;
    private String targetClass;
    private String status;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    @TableField("duration_minutes")
    private Integer timeLimit;
    private Integer maxAttempts;
    private Boolean allowRetake;
    private Boolean scorePublic;
    private Double totalScore;
    private Double passScore;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
