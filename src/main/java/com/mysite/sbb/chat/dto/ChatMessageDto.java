package com.mysite.sbb.chat.dto;

import java.util.List;

// 화면·WebSocket 으로 보내는 메시지
public record ChatMessageDto(Long id, Long roomId, String clientMessageId, String type, String content, String createdAt,
                             Sender sender, List<Attachment> attachments, List<Long> mentionIds) {

    public record Sender(Long id, String name, String initials, String photo, boolean resigned,
                         String policyLabel, String policyTone) {
    }

    public record Attachment(Long id, String name, long size, boolean image, String url) {
    }
}
