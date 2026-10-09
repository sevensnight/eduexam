package com.eduexam.dto;

import lombok.Data;

@Data
public class ExamQuestionItemDTO {
    private Long questionId;
    private Integer orderNum;
    private Integer score;
}
