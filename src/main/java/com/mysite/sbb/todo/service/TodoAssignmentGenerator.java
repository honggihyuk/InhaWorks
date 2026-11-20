package com.mysite.sbb.todo.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.organization.entity.Workplace;
import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.entity.TodoAssignment;
import com.mysite.sbb.todo.entity.TodoTask;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import com.mysite.sbb.todo.repository.TodoTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 날짜별 직원 할 일 만들기 (매일 새벽 배치 + 할 일을 새로 만들었을 때)
// [신뢰성] 멱등 : 이미 만든 (할 일, 직원, 날짜) 는 건너뛴다 → 몇 번을 다시 돌려도 결과가 같다
//  1차 방어 : 만들기 전에 그 날짜에 이미 배정된 직원 id 를 읽어서 빼기
//  2차 방어 : DB unique 제약 (task_id, member_id, work_date)
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoAssignmentGenerator {

    private final TodoTaskRepository todoTaskRepository;
    private final TodoAssignmentRepository todoAssignmentRepository;
    private final MemberRepository memberRepository;
    private final PlatformTransactionManager transactionManager;

    // 그 날짜의 모든 할 일 → 만든 개수
    // [방어] 할 일마다 새 트랜잭션(REQUIRES_NEW) : 한 할 일에서 오류가 나도 다른 할 일의 배정은 커밋된다 (건별 실패 격리)
    //  실패한 할 일이 있으면 마지막에 BatchPartialFailureException → 배치 기록(JobExecution)이 FAILED 로 남고
    //  복구(catch-up)·관리자 재실행이 다시 돌린다. 이미 만든 배정은 멱등이라 다시 돌려도 중복되지 않는다
    public int generate(LocalDate date) {
        TransactionTemplate each = new TransactionTemplate(transactionManager);
        each.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        List<Long> taskIds = read.execute(s -> todoTaskRepository.findActiveOn(date).stream().map(TodoTask::getId).toList());
        int created = 0;
        List<String> failed = new ArrayList<>();
        for (Long taskId : taskIds == null ? List.<Long>of() : taskIds) {
            try {
                Integer n = each.execute(s -> generateFor(todoTaskRepository.findById(taskId).orElseThrow(), date));
                created += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.error("=======> 할 일 #{} {} 배정 실패 (다른 할 일은 계속) : {}", taskId, date, e.toString());
                failed.add("#" + taskId);
            }
        }
        if (!failed.isEmpty()) {
            throw new BatchPartialFailureException("할 일 " + failed.size() + "건 배정 실패 " + failed
                    + " (나머지 " + created + "건은 만들었어요. 다시 실행하면 빠진 것만 채워요)", created);
        }
        return created;
    }

    // 일부 할 일만 실패했을 때 : 만든 개수를 함께 알린다
    public static class BatchPartialFailureException extends RuntimeException {
        private final int created;

        public BatchPartialFailureException(String message, int created) {
            super(message);
            this.created = created;
        }

        public int getCreated() {
            return created;
        }
    }

    @Transactional
    public int generateFor(TodoTask task, LocalDate date) {
        if (!task.occursOn(date)) {
            return 0;
        }
        Set<Long> already = new HashSet<>(todoAssignmentRepository.findMemberIds(task.getId(), date));
        int created = 0;
        for (Member member : targets(task)) {
            if (already.add(member.getId())) {
                todoAssignmentRepository.save(TodoAssignment.builder()
                        .task(task)
                        .member(member)
                        .workDate(date)
                        .status(AssignmentStatus.NOT_SUBMITTED)
                        .build());
                created++;
            }
        }
        if (created > 0) {
            log.info("=======> 할 일 '{}' {} : {}명 배정", task.getTitle(), date, created);
        }
        return created;
    }

    // 대상 = 직접 고른 직원 + 고른 근무지(매장)의 재직 직원
    public Set<Member> targets(TodoTask task) {
        Set<Member> targets = new LinkedHashSet<>();
        task.getAssignees().stream().filter(Member::isActive).forEach(targets::add);
        List<Long> workplaceIds = task.getWorkplaces().stream().map(Workplace::getId).toList();
        if (!workplaceIds.isEmpty()) {
            targets.addAll(memberRepository.findByActiveTrueAndWorkplaceIdIn(workplaceIds));
        }
        return targets;
    }
}
