package com.eduexam.dto;

import lombok.Data;

@Data
public class AnswerItemDTO {
    private Long questionId;
    private String userAnswer;
}
