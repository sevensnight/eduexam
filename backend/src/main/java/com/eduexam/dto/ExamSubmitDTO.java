package com.eduexam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;

@Data
public class ExamSubmitDTO {
    @NotNull(message = "考试记录不能为空")
    private Long recordId;

    /** Compare-and-set revision shared by draft saves and the final submit. */
    @NotNull(message = "答卷版本不能为空")
    @Min(value = 1, message = "草稿版本无效")
    private Integer draftVersion;

    @NotEmpty(message = "答题内容不能为空")
    @Valid
    private List<AnswerItemDTO> answers;
}
