package com.eduexam.dto;

import lombok.Data;
import java.util.List;

@Data
public class EssayGradeDTO {
    private List<EssayGradeItemDTO> grades;

    @Data
    public static class EssayGradeItemDTO {
        private Long answerId;
        private String isCorrect;
        private Double scoreGot;
    }
}
