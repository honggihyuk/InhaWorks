package com.mysite.sbb.chat.entity;

import jakarta.persistence.*;
import lombok.*;

// 채팅 첨부 : 공개 폴더가 아닌 비공개 폴더에 UUID 이름으로 저장하고, 컨트롤러가 참여자인지 확인한 뒤 내려준다 (SEC-05, 06)
@Entity
@Table(name = "chat_attachment", indexes = @Index(name = "idx_chat_attachment_message", columnList = "message_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChatMessage message;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, length = 255)
    private String storedName;      // 비공개 폴더 기준 상대 경로 (chat/uuid.png)

    private long size;

    private boolean image;
}
