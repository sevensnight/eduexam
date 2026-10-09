package com.eduexam.dto;

import lombok.Data;

@Data
public class QuestionQueryDTO {
    private Long ownerId;
    private Integer categoryId;
    private String difficulty;
    private String questionType;
    private String keyword;
    private Long tagId;
    private String sortBy;
    private String sortOrder;
}
