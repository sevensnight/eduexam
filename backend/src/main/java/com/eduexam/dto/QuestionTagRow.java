package com.eduexam.dto;

import lombok.Data;

/** A flattened row used to fetch tags for a page of questions in one query. */
@Data
public class QuestionTagRow {
    private Long questionId;
    private Long id;
    private String name;
    private String color;
    private Long creatorId;
}
