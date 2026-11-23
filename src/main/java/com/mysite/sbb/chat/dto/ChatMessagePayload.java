package com.mysite.sbb.chat.dto;

// Outbox 이벤트 CHAT_MESSAGE_CREATED 내용 : 본문은 넣지 않고 id 만 (재처리 모니터에 대화 내용이 드러나지 않게)
public record ChatMessagePayload(Long messageId) {
}
