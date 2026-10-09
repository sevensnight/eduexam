package com.eduexam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduexam.domain.ExamQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {
}
