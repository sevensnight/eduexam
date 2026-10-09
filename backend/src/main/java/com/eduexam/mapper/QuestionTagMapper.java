package com.eduexam.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuestionTagMapper {
    @Insert("INSERT IGNORE INTO question_tags(question_id, tag_id) VALUES(#{questionId}, #{tagId})")
    void insert(Long questionId, Long tagId);

    @Delete("DELETE FROM question_tags WHERE question_id = #{questionId}")
    void deleteByQuestionId(Long questionId);

    @Delete("DELETE FROM question_tags WHERE question_id = #{questionId} AND tag_id = #{tagId}")
    void delete(Long questionId, Long tagId);

    @Select("SELECT tag_id FROM question_tags WHERE question_id = #{questionId}")
    List<Long> findTagIdsByQuestionId(Long questionId);

    @Select("SELECT DISTINCT qt.tag_id FROM question_tags qt JOIN questions q ON q.id = qt.question_id WHERE q.creator_id = #{teacherId}")
    List<Long> findTagIdsByTeacher(Long teacherId);

    @Select("SELECT qt.question_id FROM question_tags qt JOIN questions q ON q.id = qt.question_id WHERE qt.tag_id = #{tagId} AND q.creator_id != #{teacherId} LIMIT 1")
    List<Long> findTagUsageByOtherTeachers(Long tagId, Long teacherId);
}
