package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("answers")
public class Answer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long questionId;
    private String userAnswer;
    private String isCorrect;
    private Double scoreGot;
}
