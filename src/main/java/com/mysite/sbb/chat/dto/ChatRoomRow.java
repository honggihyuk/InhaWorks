package com.mysite.sbb.chat.dto;

import com.mysite.sbb.member.entity.Member;

import java.time.LocalDateTime;
import java.util.List;

// 대화방 목록 한 줄 (얼굴 최대 3, 이름, 인원, 마지막 메시지, 시각, 안 읽은 수)
public record ChatRoomRow(Long id, String type, String typeLabel, String name, List<Member> faces, long memberCount,
                          String lastMessage, LocalDateTime lastMessageAt, long unread, boolean muted, boolean leavable,
                          String inquiryLabel, Member partner) {
}
