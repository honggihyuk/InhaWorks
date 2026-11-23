package com.mysite.sbb.outbox.service;

import com.mysite.sbb.common.util.Json;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.dto.NotificationPayload;
import com.mysite.sbb.outbox.dto.OutboxSavedEvent;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

// [신뢰성] Outbox 이벤트 저장
//  publish() 는 Propagation.MANDATORY : 반드시 업무 트랜잭션 안에서만 부를 수 있다.
//  → 업무 데이터 변경과 이벤트 저장이 "하나의 트랜잭션"으로 묶인다 (둘 다 커밋되거나 둘 다 롤백).
//  저장한 뒤 OutboxSavedEvent 를 발행해 두면, 커밋 직후 OutboxRelay 가 바로(비동기) 처리한다 (빠른 경로).
//  빠른 경로가 실패하거나 서버가 죽어도 행은 DB 에 PENDING 으로 남아 있으므로 스케줄러가 다시 처리한다.
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final int MONITOR_PAGE_SIZE = 20;

    // [REL-51] 재처리 경로는 별도 로거 이름으로 남긴다 (OPS-03)
    public static final Logger REPROCESS_LOG = LoggerFactory.getLogger("com.mysite.sbb.reprocess");

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries;

    // idempotencyKey 가 같은 이벤트가 이미 있으면 새로 만들지 않고 null 을 돌려준다
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publish(String eventType, String aggregateType, Long aggregateId, Object payload, String idempotencyKey) {
        if (idempotencyKey != null && outboxEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(Json.write(payload))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(maxRetries)
                .nextRetryAt(now)
                .idempotencyKey(idempotencyKey)
                .created(now)
                .build());
        eventPublisher.publishEvent(new OutboxSavedEvent(event.getId()));
        return event;
    }

    // 알림 이벤트 (가장 많이 쓰는 모양). 받는 사람이 없으면 아무것도 하지 않는다
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent notify(Collection<Member> recipients, NotificationType type, String title, String message,
                              String link, String aggregateType, Long aggregateId, String idempotencyKey) {
        List<Long> ids = recipients.stream().filter(Objects::nonNull).map(Member::getId).distinct().toList();
        if (ids.isEmpty()) {
            return null;
        }
        return publish(NotificationOutboxHandler.TYPE, aggregateType, aggregateId,
                new NotificationPayload(ids, type, title, message, link), idempotencyKey);
    }

    // ---------- 재처리 모니터 ----------

    // 상태·종류로 거른 이벤트 한 페이지 (최신순). 범위를 벗어난 번호는 마지막 페이지로
    @Transactional(readOnly = true)
    public Page<OutboxEvent> getEventPage(OutboxStatus status, String eventType, int page) {
        Page<OutboxEvent> result = findEvents(status, eventType, Math.max(page, 0));
        if (result.getContent().isEmpty() && result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            return findEvents(status, eventType, result.getTotalPages() - 1);
        }
        return result;
    }

    private Page<OutboxEvent> findEvents(OutboxStatus status, String eventType, int page) {
        Pageable pageable = PageRequest.of(page, MONITOR_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
        boolean typed = eventType != null && !eventType.isBlank();
        if (status != null && typed) return outboxEventRepository.findByStatusAndEventType(status, eventType, pageable);
        if (status != null) return outboxEventRepository.findByStatus(status, pageable);
        if (typed) return outboxEventRepository.findByEventType(eventType, pageable);
        return outboxEventRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<String> getEventTypes() {
        return outboxEventRepository.findEventTypes();
    }

    @Transactional(readOnly = true)
    public long count(OutboxStatus status) {
        return outboxEventRepository.countByStatus(status);
    }

    // 관리자 수동 재처리 : DEAD(또는 아무 상태) → PENDING, 재시도 횟수 0 으로
    @Transactional
    public void reprocess(Long id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("이벤트를 찾을 수 없어요."));
        REPROCESS_LOG.info("수동 재처리 : 이벤트 #{} 종류={} 이전 상태={} 시도={} 오류={}", event.getId(), event.getEventType(),
                event.getStatus(), event.getRetryCount(), event.getLastError());
        event.resetForReprocess(LocalDateTime.now());
        eventPublisher.publishEvent(new OutboxSavedEvent(event.getId()));
    }
}
