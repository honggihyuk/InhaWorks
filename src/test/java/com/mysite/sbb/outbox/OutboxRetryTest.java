package com.mysite.sbb.outbox;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.notification.repository.NotificationRepository;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import com.mysite.sbb.outbox.service.OutboxEventHandler;
import com.mysite.sbb.outbox.service.OutboxProcessor;
import com.mysite.sbb.outbox.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Outbox : 실패 → 재시도(retryCount++) → 최대 횟수에서 DEAD → 관리자 재처리 → 성공(DONE)
//          알림 핸들러 멱등성, 멱등성 키 중복 방지, MANDATORY 전파
@SpringBootTest
class OutboxRetryTest {

    static final String FLAKY = "TEST_FLAKY";
    static final AtomicBoolean FAIL = new AtomicBoolean(true);   // true 면 핸들러가 예외를 던진다

    @TestConfiguration
    static class Config {
        @Bean
        OutboxEventHandler flakyHandler() {
            return new OutboxEventHandler() {
                @Override
                public String eventType() {
                    return FLAKY;
                }

                @Override
                public void handle(OutboxEvent event) {
                    if (FAIL.get()) throw new IllegalStateException("외부 시스템 일시 장애 (테스트)");
                }
            };
        }
    }

    @Autowired OutboxService outboxService;
    @Autowired OutboxProcessor outboxProcessor;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TransactionTemplate tx;

    @Test
    @DisplayName("실패할 때마다 retryCount 증가 → max-retries(3) 에서 DEAD → 재처리하면 PENDING, 성공하면 DONE")
    void retryThenDeadThenReprocess() {
        FAIL.set(true);
        Long id = tx.execute(s -> outboxService.publish(FLAKY, "Test", 1L, Map.of("hello", "world"), null).getId());

        assertThat(outboxProcessor.process(id)).isFalse();
        assertThat(get(id).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(get(id).getRetryCount()).isEqualTo(1);

        assertThat(outboxProcessor.process(id)).isFalse();
        assertThat(get(id).getRetryCount()).isEqualTo(2);

        assertThat(outboxProcessor.process(id)).isFalse();
        OutboxEvent dead = get(id);
        assertThat(dead.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(dead.getRetryCount()).isEqualTo(3);
        assertThat(dead.getLastError()).contains("일시 장애");

        // DEAD 는 더 이상 자동으로 처리되지 않는다
        assertThat(outboxProcessor.process(id)).isFalse();
        assertThat(get(id).getStatus()).isEqualTo(OutboxStatus.DEAD);

        // 관리자 재처리 → PENDING, 횟수 0 → 장애가 풀린 뒤 처리하면 DONE
        outboxService.reprocess(id);
        assertThat(get(id).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(get(id).getRetryCount()).isZero();

        FAIL.set(false);
        assertThat(outboxProcessor.process(id)).isTrue();
        assertThat(get(id).getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(get(id).getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("알림 이벤트 : 처리하면 받는 사람마다 알림 1개, 같은 이벤트를 다시 처리해도 알림은 늘지 않는다 (멱등)")
    void notificationHandlerIsIdempotent() {
        Member emp = memberRepository.findByUsername("emp2").orElseThrow();
        Long id = tx.execute(s -> outboxService.notify(List.of(emp), NotificationType.SYSTEM, "테스트", "멱등 확인",
                "/", "Test", 1L, null).getId());

        assertThat(outboxProcessor.process(id)).isTrue();
        assertThat(notificationRepository.existsBySourceEventKey("outbox:" + id + ":" + emp.getId())).isTrue();

        // 같은 이벤트를 강제로 다시 처리 (재시도·서버 재시작으로 두 번 오는 상황)
        outboxService.reprocess(id);
        assertThat(outboxProcessor.process(id)).isTrue();
        assertThat(notificationRepository.countBySourceEventKeyStartingWith("outbox:" + id + ":")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 멱등성 키로 두 번 발행하면 이벤트는 하나 (푸시 더블클릭)")
    void idempotencyKeyPreventsDuplicates() {
        String key = "remind:" + UUID.randomUUID() + ":1";
        OutboxEvent first = tx.execute(s -> outboxService.publish(FLAKY, "Test", 1L, Map.of(), key));
        OutboxEvent second = tx.execute(s -> outboxService.publish(FLAKY, "Test", 1L, Map.of(), key));
        assertThat(first).isNotNull();
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("Outbox 저장은 업무 트랜잭션 안에서만 (MANDATORY)")
    void publishRequiresTransaction() {
        assertThatThrownBy(() -> outboxService.publish(FLAKY, "Test", 1L, Map.of(), null))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private OutboxEvent get(Long id) {
        return outboxEventRepository.findById(id).orElseThrow();
    }
}
