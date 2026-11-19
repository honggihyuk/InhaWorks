package com.mysite.sbb.outbox.dto;

import com.mysite.sbb.notification.constant.NotificationType;

import java.util.List;

// NOTIFICATION 이벤트 내용 : 누구에게 어떤 알림을 보낼지 (JSON 으로 OutboxEvent.payload 에 저장)
public record NotificationPayload(List<Long> recipientIds,
                                  NotificationType type,
                                  String title,
                                  String message,
                                  String link) {
}
