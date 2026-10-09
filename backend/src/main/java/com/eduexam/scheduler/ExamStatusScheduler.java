package com.eduexam.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.domain.Exam;
import com.eduexam.mapper.ExamMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamStatusScheduler {

    private final ExamMapper examMapper;

    // Run every 30 seconds
    @Scheduled(fixedDelay = 30_000)
    public void updateExamStatuses() {
        LocalDateTime now = LocalDateTime.now();

        // draft → published when open_time is reached and close_time not yet passed
        List<Exam> toPublish = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getStatus, "draft")
                .isNotNull(Exam::getOpenTime)
                .le(Exam::getOpenTime, now)
                .and(w -> w.isNull(Exam::getCloseTime).or().gt(Exam::getCloseTime, now)));
        if (!toPublish.isEmpty()) {
            examMapper.update(null, new LambdaUpdateWrapper<Exam>()
                    .in(Exam::getId, toPublish.stream().map(Exam::getId).toList())
                    .set(Exam::getStatus, "published"));
            log.debug("Auto-published {} exams", toPublish.size());
        }

        // published → closed when close_time has passed
        List<Exam> toClose = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getStatus, "published")
                .isNotNull(Exam::getCloseTime)
                .le(Exam::getCloseTime, now));
        if (!toClose.isEmpty()) {
            examMapper.update(null, new LambdaUpdateWrapper<Exam>()
                    .in(Exam::getId, toClose.stream().map(Exam::getId).toList())
                    .set(Exam::getStatus, "closed"));
            log.debug("Auto-closed {} exams", toClose.size());
        }
    }
}
