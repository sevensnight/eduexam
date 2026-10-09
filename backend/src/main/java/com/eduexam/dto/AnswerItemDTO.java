package com.eduexam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerItemDTO {
    @NotNull(message = "题目不能为空")
    private Long questionId;

    @Size(max = 10000, message = "答案内容过长")
    private String userAnswer;
}
