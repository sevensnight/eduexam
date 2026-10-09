package com.eduexam.dto;

import lombok.Data;
import java.util.List;

@Data
public class CategoryReorderItemDTO {
    private Long id;
    private Long parentId;
    private Integer sortOrder;
}
