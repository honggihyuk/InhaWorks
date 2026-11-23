package com.mysite.sbb.chat.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_mention",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_mention", columnNames = {"message_id", "member_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;
}
