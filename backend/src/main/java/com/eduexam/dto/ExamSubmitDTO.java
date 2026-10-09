package com.eduexam.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExamSubmitDTO {
    private Long recordId;
    private List<AnswerItemDTO> answers;
}
