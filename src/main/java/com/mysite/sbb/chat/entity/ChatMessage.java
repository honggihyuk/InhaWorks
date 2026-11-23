package com.mysite.sbb.chat.entity;

import com.mysite.sbb.chat.constant.ChatMessageType;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 채팅 메시지
// [신뢰성] REL-20 : (room, sender, clientMessageId) unique → 같은 메시지를 다시 보내도 한 번만 저장된다
@Entity
@Table(name = "chat_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_message_client", columnNames = {"room_id", "sender_id", "client_message_id"}),
        indexes = @Index(name = "idx_chat_message_room", columnList = "room_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member sender;

    @Column(name = "client_message_id", nullable = false, length = 64)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "message_type", nullable = false, length = 10)
    private ChatMessageType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
