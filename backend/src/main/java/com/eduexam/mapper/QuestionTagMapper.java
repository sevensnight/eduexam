package com.eduexam.mapper;

import com.eduexam.dto.QuestionTagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface QuestionTagMapper {
    @Insert("INSERT IGNORE INTO question_tags(question_id, tag_id) VALUES(#{questionId}, #{tagId})")
    void insert(Long questionId, Long tagId);

    @Delete("DELETE FROM question_tags WHERE question_id = #{questionId}")
    void deleteByQuestionId(Long questionId);

    @Delete("DELETE FROM question_tags WHERE tag_id = #{tagId}")
    void deleteByTagId(Long tagId);

    @Delete("DELETE FROM question_tags WHERE question_id = #{questionId} AND tag_id = #{tagId}")
    void delete(Long questionId, Long tagId);

    @Select("SELECT tag_id FROM question_tags WHERE question_id = #{questionId}")
    List<Long> findTagIdsByQuestionId(Long questionId);

    @Select("SELECT DISTINCT qt.tag_id FROM question_tags qt JOIN questions q ON q.id = qt.question_id WHERE q.creator_id = #{teacherId}")
    List<Long> findTagIdsByTeacher(Long teacherId);

    @Select("SELECT qt.question_id FROM question_tags qt JOIN questions q ON q.id = qt.question_id WHERE qt.tag_id = #{tagId} AND (q.creator_id IS NULL OR q.creator_id != #{teacherId}) LIMIT 1")
    List<Long> findTagUsageByOtherTeachers(Long tagId, Long teacherId);

    @Select({
            "<script>",
            "SELECT qt.question_id AS question_id, t.id, t.name, t.color, t.creator_id AS creator_id",
            "FROM question_tags qt JOIN tags t ON t.id = qt.tag_id",
            "WHERE qt.question_id IN",
            "<foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "ORDER BY t.name, t.id",
            "</script>"
    })
    List<QuestionTagRow> findTagsByQuestionIds(@Param("questionIds") Collection<Long> questionIds);
}
