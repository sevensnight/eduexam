package com.eduexam.dto;

import lombok.Data;
import java.util.List;

@Data
public class QuestionCreateDTO {
    private String title;
    private String questionType;
    private String difficulty;
    private String options;
    private String answer;
    private String explanation;
    private Long categoryId;
    private List<Long> tagIds;
}
