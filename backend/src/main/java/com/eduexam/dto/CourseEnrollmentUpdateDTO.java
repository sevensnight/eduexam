package com.eduexam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CourseEnrollmentUpdateDTO {
    @Size(max = 5000, message = "一次最多调整 5000 名学生")
    private List<@NotNull(message = "学生编号不能为空") Long> studentIds;
}
