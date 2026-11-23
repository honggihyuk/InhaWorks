package com.mysite.sbb.chat.dto;

import java.util.List;

// 열린 대화방 머리글 + 참여자 + 내 권한
public record ChatRoomView(Long id, String type, String typeLabel, String name, boolean canWrite, boolean muted, boolean leavable,
                           boolean canCloseInquiry, String inquiryLabel, long lastReadMessageId, List<Person> participants) {

    public record Person(Long id, String name, String initials, String photo, String org, long lastRead,
                         String policyLabel, String policyTone) {
    }
}
