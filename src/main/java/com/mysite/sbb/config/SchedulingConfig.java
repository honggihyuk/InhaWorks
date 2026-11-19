package com.mysite.sbb.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 켜기 (Outbox 릴레이, 할 일 생성 배치, 마감 알림, 복구)
// 테스트에서는 app.scheduling.enabled=false 로 꺼서, 테스트가 직접 메서드를 불러 결과를 확인한다
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
