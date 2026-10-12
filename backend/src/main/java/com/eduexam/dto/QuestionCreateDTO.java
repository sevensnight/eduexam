package com.eduexam.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class QuestionCreateDTO {
    @Size(max = 5000, message = "题目内容不能超过5000个字符")
    private String title;
    private String questionType;
    private String difficulty;

    @Size(max = 20000, message = "选项内容不能超过20000个字符")
    private String options;
    @JsonIgnore
    private boolean optionsPresent;

    @Size(max = 10000, message = "答案不能超过10000个字符")
    private String answer;

    @Size(max = 20000, message = "解析不能超过20000个字符")
    private String explanation;
    @JsonIgnore
    private boolean explanationPresent;

    private Long categoryId;
    @JsonIgnore
    private boolean categoryIdPresent;

    private List<Long> tagIds;
    @JsonIgnore
    private boolean tagIdsPresent;

    @JsonSetter("options")
    public void setOptions(String options) {
        this.options = options;
        this.optionsPresent = true;
    }

    @JsonSetter("explanation")
    public void setExplanation(String explanation) {
        this.explanation = explanation;
        this.explanationPresent = true;
    }

    @JsonSetter("category_id")
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdPresent = true;
    }

    @JsonSetter("tag_ids")
    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsPresent = true;
    }
}
