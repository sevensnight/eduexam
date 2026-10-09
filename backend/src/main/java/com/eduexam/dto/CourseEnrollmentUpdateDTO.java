package com.eduexam.dto;

import lombok.Data;
import java.util.List;

@Data
public class CourseEnrollmentUpdateDTO {
    private List<Long> studentIds;
}
