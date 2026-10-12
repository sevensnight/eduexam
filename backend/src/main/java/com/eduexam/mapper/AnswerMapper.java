package com.eduexam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduexam.domain.Answer;
import com.eduexam.dto.QuestionAnswerStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {

    @Select({
            "<script>",
            "SELECT question_id AS question_id, COUNT(*) AS answer_count,",
            "SUM(CASE WHEN is_correct='Y' THEN 1 ELSE 0 END) AS correct_count,",
            "AVG(COALESCE(score_got, 0)) AS average_score",
            "FROM answers WHERE record_id IN",
            "<foreach collection='recordIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY question_id",
            "</script>"
    })
    List<QuestionAnswerStatRow> aggregateQuestionStats(
            @Param("recordIds") Collection<Long> recordIds);
}
