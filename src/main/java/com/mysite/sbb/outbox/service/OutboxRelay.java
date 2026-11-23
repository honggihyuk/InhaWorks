package com.mysite.sbb.outbox.service;

import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.dto.OutboxSavedEvent;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;

// [신뢰성] Outbox 릴레이 : 두 가지 길로 이벤트를 처리한다
//  ① 빠른 경로 : 업무 트랜잭션이 "커밋된 뒤"(AFTER_COMMIT) 별도 스레드(@Async)에서 바로 처리 → 알림이 거의 즉시 뜬다
//               커밋 전에 처리하면 롤백된 데이터에 대해 알림이 갈 수 있으므로 반드시 AFTER_COMMIT.
//  ② 느린 경로 : @Scheduled 로 주기적으로 PENDING 이벤트를 훑는다 → 빠른 경로가 실패·유실(서버 종료)돼도 결국 처리된다
//  두 길이 같은 이벤트를 동시에 잡아도 OutboxEventRepository.claim() 이 한쪽만 통과시킨다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProcessor outboxProcessor;

    @Value("${app.outbox.fast-path:true}")
    private boolean fastPath;

    @Value("${app.outbox.stuck-timeout-seconds:300}")
    private long stuckTimeoutSeconds;

    // ① 빠른 경로
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaved(OutboxSavedEvent saved) {
        if (fastPath) {
            outboxProcessor.process(saved.outboxEventId());
        }
    }

    // ② 느린 경로 (기본 5초마다)
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}", initialDelayString = "${app.outbox.poll-initial-delay-ms:10000}")
    public void poll() {
        pollOnce();
    }

    public int pollOnce() {
        LocalDateTime now = LocalDateTime.now();
        // PROCESSING 인 채로 오래 멈춘 이벤트(처리 중 서버 종료 등)를 PENDING 으로 되살린다
        int released = outboxEventRepository.releaseStuck(OutboxStatus.PENDING, OutboxStatus.PROCESSING,
                now.minusSeconds(stuckTimeoutSeconds));
        if (released > 0) {
            OutboxService.REPROCESS_LOG.warn("멈춘 이벤트 {}건을 PENDING 으로 되살림 (기준 {}초)", released, stuckTimeoutSeconds);
        }
        List<OutboxEvent> due = outboxEventRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(OutboxStatus.PENDING, now);
        int done = 0;
        for (OutboxEvent event : due) {
            if (outboxProcessor.process(event.getId())) done++;
        }
        return done;
    }
}
