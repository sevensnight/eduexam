package com.eduexam.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamRecordMapperSqlContractTest {

    @Test
    void ownerStatisticsUseTheExamsCreatorColumn() throws NoSuchMethodException {
        Method method = ExamRecordMapper.class.getMethod("countForExamOwner", Long.class, String.class);
        String sql = String.join("\n", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("e.creator_id"));
        assertFalse(sql.contains("e.teacher_id"));
    }
}
