package com.eduexam.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamCreateDTO {
    @NotBlank(message = "考试标题不能为空")
    @Size(max = 256, message = "考试标题不能超过 256 个字符")
    private String title;
    @Size(max = 20000, message = "考试说明不能超过 20000 个字符")
    private String description;
    @Positive(message = "教师编号必须为正整数")
    private Long teacherId;
    @Positive(message = "课程编号必须为正整数")
    private Long courseId;
    @Size(max = 64, message = "目标班级不能超过 64 个字符")
    private String targetClass;
    @Pattern(regexp = "draft|published|closed", message = "考试状态无效")
    private String status;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    @JsonAlias("duration_minutes")
    @Min(value = 1, message = "考试时长必须大于 0")
    @Max(value = 1440, message = "考试时长不能超过 1440 分钟")
    private Integer timeLimit;
    @Min(value = 1, message = "最大考试次数必须大于 0")
    @Max(value = 100, message = "最大考试次数不能超过 100")
    private Integer maxAttempts;
    private Boolean allowRetake;
    private Boolean scorePublic;
    private Double totalScore;
    @PositiveOrZero(message = "及格分不能小于 0")
    private Double passScore;
    @Size(max = 1000, message = "单场考试最多配置 1000 道题")
    private List<@Valid ExamQuestionItemDTO> questions;
}
