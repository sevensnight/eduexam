package com.eduexam.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamUpdateDTO {
    private String title;
    private String description;
    private Long teacherId;
    private Long courseId;
    @JsonIgnore
    private boolean courseIdPresent;
    private String targetClass;
    @JsonIgnore
    private boolean targetClassPresent;
    private String status;
    private LocalDateTime openTime;
    @JsonIgnore
    private boolean openTimePresent;
    private LocalDateTime closeTime;
    @JsonIgnore
    private boolean closeTimePresent;
    @JsonAlias("duration_minutes")
    private Integer timeLimit;
    private Integer maxAttempts;
    private Boolean allowRetake;
    private Boolean scorePublic;
    private Double totalScore;
    private Double passScore;
    private List<ExamQuestionItemDTO> questions;

    @JsonSetter("course_id")
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
        this.courseIdPresent = true;
    }

    @JsonSetter("target_class")
    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
        this.targetClassPresent = true;
    }

    @JsonSetter("open_time")
    public void setOpenTime(LocalDateTime openTime) {
        this.openTime = openTime;
        this.openTimePresent = true;
    }

    @JsonSetter("close_time")
    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
        this.closeTimePresent = true;
    }
}
