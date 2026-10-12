package com.eduexam.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamUpdateDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omittedNullableFieldsRemainAbsent() throws Exception {
        ExamUpdateDTO dto = objectMapper.readValue("{\"status\":\"closed\"}", ExamUpdateDTO.class);

        assertFalse(dto.isCourseIdPresent());
        assertFalse(dto.isTargetClassPresent());
        assertFalse(dto.isOpenTimePresent());
        assertFalse(dto.isCloseTimePresent());
        assertFalse(dto.isMaxAttemptsPresent());
        assertFalse(dto.isPassScorePresent());
    }

    @Test
    void explicitNullsAreTrackedSoExistingValuesCanBeCleared() throws Exception {
        ExamUpdateDTO dto = objectMapper.readValue("""
                {
                  "course_id": null,
                  "target_class": null,
                  "open_time": null,
                  "close_time": null,
                  "max_attempts": null,
                  "pass_score": null
                }
                """, ExamUpdateDTO.class);

        assertTrue(dto.isCourseIdPresent());
        assertTrue(dto.isTargetClassPresent());
        assertTrue(dto.isOpenTimePresent());
        assertTrue(dto.isCloseTimePresent());
        assertTrue(dto.isMaxAttemptsPresent());
        assertTrue(dto.isPassScorePresent());
        assertNull(dto.getCourseId());
        assertNull(dto.getTargetClass());
        assertNull(dto.getOpenTime());
        assertNull(dto.getCloseTime());
        assertNull(dto.getMaxAttempts());
        assertNull(dto.getPassScore());
    }
}
