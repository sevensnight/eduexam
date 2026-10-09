package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_records")
public class ExamRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long studentId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private Double totalScore;
    private Integer version;
    private Integer attemptCount;
    private Boolean essayGraded;
}
