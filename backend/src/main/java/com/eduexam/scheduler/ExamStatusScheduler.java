package com.eduexam.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.domain.Exam;
import com.eduexam.domain.ExamRecord;
import com.eduexam.mapper.ExamMapper;
import com.eduexam.mapper.ExamRecordMapper;
import com.eduexam.service.ExamLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamStatusScheduler {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final ExamMapper examMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamLifecycleService examLifecycleService;

    @PostConstruct
    public void normalizeLegacyRecordStatuses() {
        int graded = examRecordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getStatus, "completed")
                .set(ExamRecord::getStatus, "graded"));
        int inProgress = examRecordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getStatus, "started")
                .set(ExamRecord::getStatus, "in_progress"));
        if (graded > 0 || inProgress > 0)
            log.info("Normalized legacy exam record statuses: completed={}, started={}", graded, inProgress);
    }

    // Run every 30 seconds
    @Scheduled(fixedDelay = 30_000)
    public void updateExamStatuses() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);

        // draft → published when open_time is reached and close_time not yet passed
        List<Exam> toPublish = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getStatus, "draft")
                .isNotNull(Exam::getOpenTime)
                .le(Exam::getOpenTime, now)
                .and(w -> w.isNull(Exam::getCloseTime).or().gt(Exam::getCloseTime, now)));
        int published = 0;
        for (Exam exam : toPublish) {
            try {
                if (examLifecycleService.publishExam(exam.getId())) published++;
            } catch (RuntimeException ex) {
                // An invalid scheduled draft stays a draft; one broken exam must
                // never prevent the remaining due exams from being processed.
                log.warn("Skipped invalid scheduled exam {}: {}", exam.getId(), ex.getMessage());
            }
        }
        if (published > 0) log.debug("Auto-published {} exams", published);

        // published → closed when close_time has passed
        List<Exam> toClose = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getStatus, "published")
                .isNotNull(Exam::getCloseTime)
                .le(Exam::getCloseTime, now));
        int closed = 0;
        for (Exam exam : toClose) {
            try {
                if (examLifecycleService.closeExam(exam.getId())) closed++;
            } catch (RuntimeException ex) {
                log.error("Failed to close scheduled exam {}", exam.getId(), ex);
            }
        }
        if (closed > 0) log.debug("Auto-closed {} exams", closed);
    }
}
