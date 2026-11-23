package com.mysite.sbb.outbox.service;

import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// [신뢰성] Outbox 이벤트 한 건 처리
//  1) 선점 (claim)        : update ... where status = PENDING  → 1 이 나온 쪽만 계속 (트랜잭션 ①)
//  2) 처리 + DONE 표시    : 핸들러 실행과 DONE 을 같은 트랜잭션으로 (트랜잭션 ②, REQUIRES_NEW)
//  3) 실패하면            : ②는 롤백되고, 별도 트랜잭션 ③에서 retryCount++, 지수 백오프, 최대 횟수 초과 시 DEAD
//  트랜잭션을 나누는 이유 : 핸들러가 실패해도 "실패 기록"은 반드시 남아야 하기 때문 (②가 롤백되어도 ③은 커밋)
@Component
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final Map<String, OutboxEventHandler> handlers;
    private final TransactionTemplate tx;

    @Value("${app.outbox.base-backoff-seconds:10}")
    private long baseBackoffSeconds;

    @Value("${app.outbox.max-backoff-seconds:3600}")
    private long maxBackoffSeconds;

    public OutboxProcessor(OutboxEventRepository outboxEventRepository, List<OutboxEventHandler> handlerList,
                           PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.handlers = handlerList.stream().collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity()));
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 처리했으면 true (선점 못 했거나 실패하면 false)
    public boolean process(Long eventId) {
        Integer claimed = tx.execute(s -> outboxEventRepository.claim(eventId, OutboxStatus.PENDING,
                OutboxStatus.PROCESSING, LocalDateTime.now()));
        if (claimed == null || claimed == 0) {
            return false;   // 다른 쪽(릴레이/빠른 경로)이 이미 가져갔거나, 아직 재시도 시각이 아님
        }
        try {
            tx.executeWithoutResult(s -> {
                OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
                OutboxEventHandler handler = handlers.get(event.getEventType());
                if (handler == null) {
                    throw new IllegalStateException("처리기가 없는 이벤트 종류 : " + event.getEventType());
                }
                handler.handle(event);
                if (event.getRetryCount() > 0) {
                    OutboxService.REPROCESS_LOG.info("재시도 성공 : 이벤트 #{} 종류={} 시도={}", eventId, event.getEventType(), event.getRetryCount() + 1);
                }
                event.markDone(LocalDateTime.now());
            });
            return true;
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(s -> outboxEventRepository.findById(eventId).ifPresent(event -> {
                event.markFailed(error, baseBackoffSeconds, maxBackoffSeconds, LocalDateTime.now());
                if (event.getStatus() == OutboxStatus.DEAD) {
                    OutboxService.REPROCESS_LOG.error("DEAD : 이벤트 #{} 종류={} 시도={} 오류={}", eventId, event.getEventType(), event.getRetryCount(), summary(error));
                } else {
                    OutboxService.REPROCESS_LOG.warn("실패 : 이벤트 #{} 종류={} 시도={} 다음 시도={} 오류={}", eventId, event.getEventType(),
                            event.getRetryCount(), event.getNextRetryAt(), summary(error));
                }
            }));
            return false;
        }
    }

    private static String summary(String error) {
        return error.length() > 200 ? error.substring(0, 200) + "…" : error;
    }
}
