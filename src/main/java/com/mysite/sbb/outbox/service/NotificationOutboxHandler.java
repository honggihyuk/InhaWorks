package com.mysite.sbb.outbox.service;

import com.mysite.sbb.common.util.Json;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.notification.service.NotificationService;
import com.mysite.sbb.outbox.dto.NotificationPayload;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// NOTIFICATION 이벤트 → 받는 사람마다 앱 안 알림 만들기
// 멱등 : 알림의 sourceEventKey = "outbox:{이벤트ID}:{받는사람ID}" (unique) 로 중복 생성을 막는다
@Component
@RequiredArgsConstructor
public class NotificationOutboxHandler implements OutboxEventHandler {

    public static final String TYPE = "NOTIFICATION";

    private final NotificationService notificationService;
    private final MemberRepository memberRepository;

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        NotificationPayload p = Json.read(event.getPayload(), NotificationPayload.class);
        for (Long recipientId : p.recipientIds()) {
            memberRepository.findById(recipientId).ifPresent(recipient ->
                    notificationService.createIfAbsent("outbox:" + event.getId() + ":" + recipientId,
                            recipient, p.type(), p.title(), p.message(), p.link()));
        }
    }
}
