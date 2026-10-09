package com.eduexam.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamCreateDTO {
    private String title;
    private String description;
    private Long teacherId;
    private Long courseId;
    private String targetClass;
    private String status;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private Integer timeLimit;
    private Integer maxAttempts;
    private Boolean allowRetake;
    private Boolean scorePublic;
    private Double totalScore;
    private Double passScore;
    private List<ExamQuestionItemDTO> questions;
}
