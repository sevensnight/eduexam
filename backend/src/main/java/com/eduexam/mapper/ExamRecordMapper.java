package com.eduexam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduexam.domain.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    @Select("""
            SELECT id, exam_id, student_id, status, started_at, submitted_at,
                   total_score, version, attempt_count, essay_graded
            FROM (
                SELECT r.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.student_id
                           ORDER BY r.total_score DESC, r.submitted_at ASC, r.id ASC
                       ) AS row_num
                FROM exam_records r
                WHERE r.exam_id = #{examId} AND r.status = 'graded'
            ) ranked
            WHERE row_num = 1
            ORDER BY total_score DESC, submitted_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ExamRecord> findBestGradedAttempts(@Param("examId") Long examId,
                                             @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM exam_records r
            JOIN exams e ON e.id = r.exam_id
            WHERE (#{teacherId} IS NULL OR e.creator_id = #{teacherId})
              AND (#{status} IS NULL OR r.status = #{status})
            """)
    long countForExamOwner(@Param("teacherId") Long teacherId,
                           @Param("status") String status);
}
