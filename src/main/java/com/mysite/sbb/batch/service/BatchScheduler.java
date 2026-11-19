package com.mysite.sbb.batch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 배치 일정 (SchedulingConfig 가 켜져 있을 때만 @Scheduled 가 돈다)
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final BatchJobs batchJobs;

    @Value("${app.batch.catch-up-on-startup:true}")
    private boolean catchUpOnStartup;

    // 매일 00:05 오늘 할 일 만들기
    @Scheduled(cron = "${app.batch.generate-cron:0 5 0 * * *}")
    public void generateToday() {
        batchJobs.generate(LocalDate.now(), false);
    }

    // 1분마다 : 마감 시각이 지났으면 오늘 마감 알림 (하루 한 번)
    @Scheduled(cron = "0 * * * * *")
    public void deadline() {
        batchJobs.deadlineIfDue(LocalDateTime.now());
    }

    // 주기적 복구 : 누락된 날짜 / 실패한 배치 다시 돌리기
    @Scheduled(fixedDelayString = "${app.batch.recovery-interval-ms:600000}", initialDelayString = "${app.batch.recovery-interval-ms:600000}")
    public void recover() {
        batchJobs.catchUp();
    }

    // 서버 시작 직후 : 꺼져 있던 동안 빠진 날짜 채우기
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (catchUpOnStartup) {
            batchJobs.catchUp();
        }
    }
}
