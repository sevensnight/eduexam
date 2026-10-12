package com.eduexam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;

@Data
public class EssayGradeDTO {
    @NotEmpty(message = "批改内容不能为空")
    @Valid
    private List<EssayGradeItemDTO> grades;

    @Data
    public static class EssayGradeItemDTO {
        @NotNull(message = "答案编号不能为空")
        private Long answerId;

        @NotNull(message = "判定结果不能为空")
        @Pattern(regexp = "[YNP]", message = "判定结果必须是 Y、N 或 P")
        private String isCorrect;

        @NotNull(message = "得分不能为空")
        @DecimalMin(value = "0.0", message = "得分不能为负数")
        private Double scoreGot;
    }
}
