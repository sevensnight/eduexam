package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.eduexam.common.AppException;
import com.eduexam.mapper.CategoryMapper;
import com.eduexam.mapper.QuestionMapper;
import com.eduexam.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock CategoryMapper categoryMapper;
    @Mock QuestionMapper questionMapper;
    @Mock UserMapper userMapper;

    @Test
    void reorderRejectsSiblingNamesThatMysqlCollationConsidersEqual() {
        when(categoryMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(java.util.List.of(Map.of("name", "Math", "duplicate_count", 2)));

        CategoryController controller = new CategoryController(categoryMapper, questionMapper, userMapper);
        AppException error = assertThrows(AppException.class,
                controller::ensureNoSiblingNameDuplicates);

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }
}
