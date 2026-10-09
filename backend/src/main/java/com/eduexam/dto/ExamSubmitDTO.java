package com.eduexam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ExamSubmitDTO {
    @NotNull(message = "考试记录不能为空")
    private Long recordId;

    @NotEmpty(message = "答题内容不能为空")
    @Valid
    private List<AnswerItemDTO> answers;
}
