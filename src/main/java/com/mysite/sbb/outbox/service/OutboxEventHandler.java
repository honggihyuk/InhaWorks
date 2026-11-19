package com.mysite.sbb.outbox.service;

import com.mysite.sbb.outbox.entity.OutboxEvent;

// Outbox 이벤트 처리기. eventType 마다 하나씩 만든다.
// [신뢰성] 처리기는 반드시 멱등(idempotent)해야 한다 : 같은 이벤트가 두 번 와도 결과가 한 번 처리한 것과 같아야 함
//  (재시도, 서버 재시작 후 되살리기 때문에 "최소 한 번(at-least-once)" 전달이기 때문)
public interface OutboxEventHandler {

    String eventType();

    void handle(OutboxEvent event);
}
