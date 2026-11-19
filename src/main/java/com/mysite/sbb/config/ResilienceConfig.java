package com.mysite.sbb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskExecutor;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// [신뢰성] 재시도 + 비동기 설정
//
// @EnableResilientMethods : Spring Framework 7 에 들어간 @Retryable / @ConcurrencyLimit 을 켠다 (별도 라이브러리 필요 없음)
//   order = HIGHEST_PRECEDENCE 로 "재시도 프록시를 트랜잭션 프록시보다 바깥"에 둔다.
//   → 재시도할 때마다 @Transactional 이 새 트랜잭션을 연다.
//     (반대로 트랜잭션 안쪽에서 재시도하면, 이미 실패로 표시된 같은 트랜잭션/영속성 컨텍스트를 다시 쓰게 되어 의미가 없다)
//
// @EnableAsync : Outbox 빠른 경로(커밋 직후 처리)를 요청 스레드가 아닌 별도 스레드에서 돌린다
@Configuration
@EnableAsync
@EnableResilientMethods(order = Ordered.HIGHEST_PRECEDENCE)
public class ResilienceConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);      // 넘치면 예외 → 이벤트는 DB 에 PENDING 으로 남아 있으므로 스케줄러가 처리
        executor.setThreadNamePrefix("outbox-");
        executor.initialize();
        return executor;
    }
}
