package com.mysite.sbb.chat.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 대화방 참여자. 읽음 위치는 "마지막으로 읽은 메시지 id" 하나만 저장하고 더 큰 값으로만 바꾼다 (REL-22)
@Entity
@Table(name = "chat_participant",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_participant", columnNames = {"room_id", "member_id"}),
        indexes = @Index(name = "idx_chat_participant_member", columnList = "member_id, left_room"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @Builder.Default
    @Column(nullable = false)
    private long lastReadMessageId = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean muted = false;

    @Builder.Default
    @Column(name = "left_room", nullable = false)
    private boolean leftRoom = false;

    private LocalDateTime joinedAt;
}
