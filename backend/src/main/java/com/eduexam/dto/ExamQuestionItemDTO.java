package com.eduexam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ExamQuestionItemDTO {
    @NotNull(message = "试题编号不能为空")
    @Positive(message = "试题编号必须为正整数")
    private Long questionId;
    private Integer orderNum;
    @Positive(message = "题目分值必须大于 0")
    private Double score;
}
