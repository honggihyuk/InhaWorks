package com.mysite.sbb.todo.service;

import com.mysite.sbb.batch.constant.JobStatus;
import com.mysite.sbb.batch.entity.JobExecution;
import com.mysite.sbb.batch.repository.JobExecutionRepository;
import com.mysite.sbb.batch.service.BatchJobs;
import com.mysite.sbb.todo.entity.TodoTask;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import com.mysite.sbb.todo.repository.TodoTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 할 일 생성 배치 : 몇 번을 돌려도 (할 일, 직원, 날짜) 는 하나만 → 멱등
// 예시 데이터 : "매장 오픈 체크리스트" 매일, 인하점(관리자·김민수·이서연) 대상
@SpringBootTest
class TodoAssignmentGeneratorTest {

    @Autowired TodoAssignmentGenerator generator;
    @Autowired TodoTaskRepository todoTaskRepository;
    @Autowired TodoAssignmentRepository todoAssignmentRepository;
    @Autowired BatchJobs batchJobs;
    @Autowired JobExecutionRepository jobExecutionRepository;

    @Test
    @DisplayName("같은 날짜로 두 번 생성해도 두 번째는 0건 (멱등)")
    void generateIsIdempotent() {
        LocalDate date = LocalDate.now().plusDays(10);
        TodoTask task = todoTaskRepository.findAll().getFirst();

        int first = generator.generate(date);
        int second = generator.generate(date);

        assertThat(first).isGreaterThan(0);
        assertThat(second).isZero();
        assertThat(todoAssignmentRepository.countByTaskIdAndWorkDate(task.getId(), date)).isEqualTo(first);
    }

    @Test
    @DisplayName("배치 기록 : 같은 날짜 배치는 한 번만 COMPLETED, 다시 부르면 건너뛰고, 강제 재실행해도 중복 생성 없음")
    void batchJobIsTrackedAndSkipped() {
        LocalDate date = LocalDate.now().plusDays(20);
        TodoTask task = todoTaskRepository.findAll().getFirst();

        assertThat(batchJobs.generate(date, false)).isTrue();      // 실행
        assertThat(batchJobs.generate(date, false)).isFalse();     // 이미 COMPLETED → 건너뜀
        long count = todoAssignmentRepository.countByTaskIdAndWorkDate(task.getId(), date);

        JobExecution je = jobExecutionRepository.findByJobNameAndJobKey(BatchJobs.JOB_GENERATE, date.toString()).orElseThrow();
        assertThat(je.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(je.getAttempts()).isEqualTo(1);

        assertThat(batchJobs.rerun(je.getId())).isTrue();          // 수동 재실행 (force)
        assertThat(todoAssignmentRepository.countByTaskIdAndWorkDate(task.getId(), date)).isEqualTo(count);
        assertThat(jobExecutionRepository.findById(je.getId()).orElseThrow().getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("복구(catch-up) : 최근 N일 중 빠진 날짜를 채운다")
    void catchUpFillsMissedDays() {
        batchJobs.catchUp();
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= 3; i++) {
            String key = today.minusDays(i).toString();
            assertThat(jobExecutionRepository.findByJobNameAndJobKey(BatchJobs.JOB_GENERATE, key))
                    .get().extracting(JobExecution::getStatus).isEqualTo(JobStatus.COMPLETED);
        }
    }
}
