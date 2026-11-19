package com.mysite.sbb.outbox.dto;

// 스프링 내부 이벤트 : "Outbox 행을 저장했다" → 커밋된 뒤(AFTER_COMMIT) 바로 처리하는 빠른 경로에서 받는다
public record OutboxSavedEvent(Long outboxEventId) {
}
