package com.eduexam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduexam.domain.Question;
import com.eduexam.dto.QuestionQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
    IPage<Question> selectQuestionPage(Page<Question> page, @Param("q") QuestionQueryDTO query);

    void incrementUsageCount(@Param("id") Long id);
}
