package com.eduexam.dto;

import lombok.Data;

/** Lightweight aggregate used by exam insights; intentionally excludes answer text. */
@Data
public class QuestionAnswerStatRow {
    private Long questionId;
    private Long answerCount;
    private Long correctCount;
    private Double averageScore;
}
