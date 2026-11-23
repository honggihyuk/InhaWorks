package com.mysite.sbb.batch.service;

import com.mysite.sbb.batch.constant.JobStatus;
import com.mysite.sbb.batch.entity.JobExecution;
import com.mysite.sbb.batch.repository.JobExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

// [신뢰성] 배치 실행을 job_execution 테이블에 기록하면서 돌린다
//  - 이미 COMPLETED 인 (jobName, jobKey) 는 다시 돌리지 않는다 (force = true 인 수동 재실행만 예외)
//  - 실패하면 FAILED + 오류 메시지 → 복구 스케줄러가 나중에 다시 돌린다
//  - 실행 기록(시작/완료/실패)은 배치 본문과 다른 트랜잭션(REQUIRES_NEW)이라 본문이 롤백돼도 기록은 남는다
@Slf4j
@Service
public class JobExecutionService {

    // RUNNING 이 이 시간보다 오래되면 (서버가 도중에 죽은 경우) 다시 시작해도 된다
    private static final long STALE_RUNNING_MINUTES = 30;
    private static final int MONITOR_PAGE_SIZE = 20;

    private final JobExecutionRepository jobExecutionRepository;
    private final TransactionTemplate tx;

    public JobExecutionService(JobExecutionRepository jobExecutionRepository, PlatformTransactionManager transactionManager) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 실제로 실행했으면 true (건너뛰었거나 실패하면 false)
    public boolean run(String jobName, String jobKey, boolean force, Supplier<String> work) {
        Long id;
        try {
            id = tx.execute(s -> start(jobName, jobKey, force));
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            log.info("=======> 배치 {} [{}] 는 다른 곳에서 이미 시작됨 → 건너뜀", jobName, jobKey);
            return false;
        }
        if (id == null) {
            return false;
        }
        try {
            String message = work.get();
            tx.executeWithoutResult(s -> jobExecutionRepository.findById(id).ifPresent(je -> {
                je.setStatus(JobStatus.COMPLETED);
                je.setFinishedAt(LocalDateTime.now());
                je.setMessage(message);
                je.setLastError(null);
            }));
            log.info("=======> 배치 {} [{}] 완료 : {}", jobName, jobKey, message);
            return true;
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(s -> jobExecutionRepository.findById(id).ifPresent(je -> {
                je.setStatus(JobStatus.FAILED);
                je.setFinishedAt(LocalDateTime.now());
                je.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
            }));
            log.error("=======> 배치 {} [{}] 실패 : {}", jobName, jobKey, error);
            return false;
        }
    }

    private Long start(String jobName, String jobKey, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        JobExecution je = jobExecutionRepository.findByJobNameAndJobKey(jobName, jobKey).orElse(null);
        if (je == null) {
            je = JobExecution.builder().jobName(jobName).jobKey(jobKey).attempts(0).build();
        } else if (!force) {
            if (je.getStatus() == JobStatus.COMPLETED) {
                return null;                                   // 이미 끝난 배치
            }
            if (je.getStatus() == JobStatus.RUNNING && je.getStartedAt() != null
                    && je.getStartedAt().isAfter(now.minusMinutes(STALE_RUNNING_MINUTES))) {
                return null;                                   // 지금 돌고 있는 배치
            }
        }
        je.setStatus(JobStatus.RUNNING);
        je.setAttempts(je.getAttempts() + 1);
        je.setStartedAt(now);
        je.setFinishedAt(null);
        return jobExecutionRepository.saveAndFlush(je).getId();
    }

    // ---------- 재처리 모니터 ----------

    // 실행 기록 한 페이지 (최신순). 범위를 벗어난 번호는 마지막 페이지로
    public Page<JobExecution> getPage(int page) {
        Page<JobExecution> result = jobExecutionRepository.findAll(pageRequest(Math.max(page, 0)));
        if (result.getContent().isEmpty() && result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            return jobExecutionRepository.findAll(pageRequest(result.getTotalPages() - 1));
        }
        return result;
    }

    private static PageRequest pageRequest(int page) {
        return PageRequest.of(page, MONITOR_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
    }

    public List<JobExecution> getFailed() {
        return jobExecutionRepository.findByStatus(JobStatus.FAILED);
    }

    public JobExecution get(Long id) {
        return jobExecutionRepository.findById(id).orElse(null);
    }

    public long count(JobStatus status) {
        return jobExecutionRepository.countByStatus(status);
    }
}
