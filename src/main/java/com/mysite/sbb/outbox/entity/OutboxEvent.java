package com.mysite.sbb.outbox.entity;

import com.mysite.sbb.outbox.constant.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [신뢰성] Transactional Outbox 테이블
//  업무 데이터(예: 검수 결과)를 바꾸는 "같은 트랜잭션" 안에서 이 행을 같이 저장한다.
//  → 업무 데이터가 커밋되면 이벤트도 반드시 커밋되고, 롤백되면 이벤트도 같이 사라진다.
//  → 알림 같은 후처리는 이 테이블을 읽어서 따로 처리하므로 "저장은 됐는데 알림이 안 갔다"는 일이 없다.
@Entity
@Table(name = "outbox_event", indexes = @Index(name = "idx_outbox_status_next", columnList = "status, next_retry_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String eventType;               // 어떤 핸들러가 처리할지 (예: NOTIFICATION)

    @Column(length = 50)
    private String aggregateType;           // 어떤 업무에서 나온 이벤트인지 (예: TodoAssignment)

    private Long aggregateId;

    @Column(columnDefinition = "TEXT")
    private String payload;                 // 이벤트 내용 (JSON)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    private int retryCount;                 // 실패한 횟수

    private int maxRetries;                 // 이 횟수만큼 실패하면 DEAD

    private LocalDateTime nextRetryAt;      // 이 시각 이후에 다시 시도 (지수 백오프)

    private LocalDateTime lockedAt;         // PROCESSING 으로 선점한 시각 (너무 오래되면 멈춘 것으로 보고 되살린다)

    @Column(length = 1000)
    private String lastError;

    // [신뢰성] 멱등성 키 (unique) : 같은 요청이 두 번 들어와도 (예: '푸시' 버튼 더블클릭) 이벤트는 하나만 생긴다
    @Column(unique = true, length = 150)
    private String idempotencyKey;

    private LocalDateTime created;

    private LocalDateTime processedAt;

    public void markDone(LocalDateTime now) {
        this.status = OutboxStatus.DONE;
        this.processedAt = now;
        this.lockedAt = null;
        this.lastError = null;
    }

    // 실패 : 재시도 횟수를 올리고, 남았으면 지수 백오프(base × 2^(n-1), 최대 maxBackoff) 후 다시 PENDING, 다 쓰면 DEAD
    public void markFailed(String error, long baseBackoffSeconds, long maxBackoffSeconds, LocalDateTime now) {
        this.retryCount++;
        this.lockedAt = null;
        this.lastError = error == null ? "알 수 없는 오류" : (error.length() > 1000 ? error.substring(0, 1000) : error);
        if (this.retryCount >= this.maxRetries) {
            this.status = OutboxStatus.DEAD;
        } else {
            long backoff = Math.min(maxBackoffSeconds, baseBackoffSeconds * (1L << Math.min(20, this.retryCount - 1)));
            this.status = OutboxStatus.PENDING;
            this.nextRetryAt = now.plusSeconds(backoff);
        }
    }

    // 관리자 수동 재처리 : 처음 상태로 되돌린다
    public void resetForReprocess(LocalDateTime now) {
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = now;
        this.lockedAt = null;
    }
}
