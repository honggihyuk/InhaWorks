package com.mysite.sbb.outbox.repository;

import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 릴레이 : 지금 처리할 차례인 이벤트 (오래된 것부터 100개)
    List<OutboxEvent> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(OutboxStatus status, LocalDateTime now);

    // [신뢰성] 원자적 선점 (claim)
    //  "update ... where status = PENDING" 은 DB 가 한 행씩 잠그고 처리하므로,
    //  릴레이(스케줄러)와 빠른 경로(커밋 직후 비동기)가 같은 이벤트를 동시에 집어도 한쪽만 1 을 돌려받는다.
    //  → 돌려받은 값이 1 인 쪽만 처리 → 같은 이벤트를 두 번 처리하지 않는다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = :processing, e.lockedAt = :now " +
            "where e.id = :id and e.status = :pending and e.nextRetryAt <= :now")
    int claim(@Param("id") Long id, @Param("pending") OutboxStatus pending,
              @Param("processing") OutboxStatus processing, @Param("now") LocalDateTime now);

    // [신뢰성] 멈춘 이벤트 되살리기 : 처리 중에 서버가 죽으면 PROCESSING 으로 남는다 → 일정 시간이 지나면 PENDING 으로
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = :pending, e.lockedAt = null " +
            "where e.status = :processing and e.lockedAt < :before")
    int releaseStuck(@Param("pending") OutboxStatus pending, @Param("processing") OutboxStatus processing,
                     @Param("before") LocalDateTime before);

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByStatus(OutboxStatus status);

    // 재처리 모니터
    List<OutboxEvent> findTop100ByOrderByIdDesc();

    List<OutboxEvent> findTop100ByStatusOrderByIdDesc(OutboxStatus status);

    List<OutboxEvent> findTop100ByEventTypeOrderByIdDesc(String eventType);

    List<OutboxEvent> findTop100ByStatusAndEventTypeOrderByIdDesc(OutboxStatus status, String eventType);

    @Query("select distinct e.eventType from OutboxEvent e order by e.eventType")
    List<String> findEventTypes();

    // 재처리 모니터 : 오늘 처리 완료 수
    long countByStatusAndProcessedAtGreaterThanEqual(OutboxStatus status, LocalDateTime from);
}
