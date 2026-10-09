package com.eduexam.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wrong_book")
public class WrongBookEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long questionId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;
}
