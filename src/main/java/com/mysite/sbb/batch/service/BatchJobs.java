package com.mysite.sbb.batch.service;

import com.mysite.sbb.batch.entity.JobExecution;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.todo.service.TodoAssignmentGenerator;
import com.mysite.sbb.todo.service.TodoAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 배치 작업 모음 : 실행은 모두 JobExecutionService.run() 을 거쳐 job_execution 에 기록된다
//  - TODO_ASSIGNMENT_GENERATE [날짜] : 그 날짜의 직원별 할 일 만들기 (멱등)
//  - TODO_DEADLINE_NOTIFY     [날짜] : 마감 시각에 미완료 직원에게 알림 (멱등성 키로 하루 한 번)
//
// [신뢰성] 복구 (catch-up)
//  서버가 며칠 꺼져 있었으면 그 날짜들의 할 일이 만들어지지 않았다.
//  catchUp() 은 최근 N일(app.batch.catch-up-days) 중 COMPLETED 가 아닌 날짜(없음 / FAILED / 오래 멈춘 RUNNING)를 다시 돌린다.
//  서버 시작 직후(ApplicationReadyEvent)와 주기적으로(app.batch.recovery-interval-ms) 실행된다.
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobs {

    public static final String JOB_GENERATE = "TODO_ASSIGNMENT_GENERATE";
    public static final String JOB_DEADLINE = "TODO_DEADLINE_NOTIFY";

    private final JobExecutionService jobExecutionService;
    private final TodoAssignmentGenerator generator;
    private final TodoAssignmentService todoAssignmentService;

    @Value("${app.batch.catch-up-days:7}")
    private int catchUpDays;

    @Value("${app.todo.deadline-time:18:00}")
    private String deadlineTime;

    public boolean generate(LocalDate date, boolean force) {
        return jobExecutionService.run(JOB_GENERATE, date.toString(), force,
                () -> "할 일 " + generator.generate(date) + "건 생성");
    }

    public boolean deadline(LocalDate date, boolean force) {
        return jobExecutionService.run(JOB_DEADLINE, date.toString(), force,
                () -> "마감 알림 " + todoAssignmentService.notifyDeadline(date) + "건");
    }

    // 마감 시각이 지났으면 오늘 마감 알림 (이미 했으면 COMPLETED 라서 건너뜀)
    public void deadlineIfDue(LocalDateTime now) {
        if (!now.toLocalTime().isBefore(LocalTime.parse(deadlineTime))) {
            deadline(now.toLocalDate(), false);
        }
    }

    // 최근 N일 누락분 + 실패한 배치 다시 돌리기 → 돌린 개수
    public int catchUp() {
        int ran = 0;
        LocalDate today = LocalDate.now();
        for (int i = catchUpDays; i >= 0; i--) {
            if (generate(today.minusDays(i), false)) ran++;
        }
        // 기간보다 오래된 실패 배치도 다시 시도 (생성 배치만. 지난 날 마감 알림은 의미가 없으므로 다시 보내지 않는다)
        for (JobExecution failed : jobExecutionService.getFailed()) {
            if (JOB_GENERATE.equals(failed.getJobName()) && generate(LocalDate.parse(failed.getJobKey()), false)) ran++;
        }
        if (ran > 0) log.info("=======> 배치 복구(catch-up) : {}건 실행", ran);
        return ran;
    }

    // 재처리 모니터의 '재실행' 버튼
    public boolean rerun(Long jobExecutionId) {
        JobExecution je = jobExecutionService.get(jobExecutionId);
        if (je == null) throw new BusinessException("배치 기록을 찾을 수 없어요.");
        LocalDate date = LocalDate.parse(je.getJobKey());
        return switch (je.getJobName()) {
            case JOB_GENERATE -> generate(date, true);
            case JOB_DEADLINE -> deadline(date, true);
            default -> throw new BusinessException("알 수 없는 배치 : " + je.getJobName());
        };
    }
}
