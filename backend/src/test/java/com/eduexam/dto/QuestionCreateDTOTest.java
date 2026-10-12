package com.eduexam.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionCreateDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omittedNullableFieldsRemainAbsent() throws Exception {
        QuestionCreateDTO dto = objectMapper.readValue("{\"title\":\"题目\"}", QuestionCreateDTO.class);
        assertFalse(dto.isOptionsPresent());
        assertFalse(dto.isCategoryIdPresent());
        assertFalse(dto.isExplanationPresent());
        assertFalse(dto.isTagIdsPresent());
    }

    @Test
    void explicitNullsCanClearNullableQuestionFields() throws Exception {
        QuestionCreateDTO dto = objectMapper.readValue("""
                {"options":null,"category_id":null,"explanation":null,"tag_ids":null}
                """, QuestionCreateDTO.class);
        assertTrue(dto.isOptionsPresent());
        assertTrue(dto.isCategoryIdPresent());
        assertTrue(dto.isExplanationPresent());
        assertTrue(dto.isTagIdsPresent());
        assertNull(dto.getOptions());
        assertNull(dto.getCategoryId());
    }
}
