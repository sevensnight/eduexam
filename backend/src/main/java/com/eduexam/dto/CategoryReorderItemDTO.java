package com.eduexam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryReorderItemDTO {
    @NotNull(message = "分类ID不能为空")
    private Long id;
    private Long parentId;

    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder;
}
