package com.mysite.sbb.chat.entity;

import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.constant.InquiryStatus;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.entity.Workplace;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 대화방
//  channelKey (unique) : 매장 W:{id}, 팀 T:{id}, 공지 NOTICE, 문의 I:{주제}:{직원} → 같은 방이 두 개 생기지 않는다
//  directKey  (unique) : 1:1 방 "작은id_큰id"
@Entity
@Table(name = "chat_room", indexes = @Index(name = "idx_chat_room_type", columnList = "room_type, last_message_at"))
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "room_type", nullable = false, length = 20)
    private ChatRoomType type;

    @Column(length = 80)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Workplace workplace;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    private InquiryTopic inquiryTopic;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member inquiryMember;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private InquiryStatus inquiryStatus;

    @Column(unique = true, length = 40)
    private String directKey;

    @Column(unique = true, length = 60)
    private String channelKey;

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

    @Column(length = 120)
    private String lastMessagePreview;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;       // 팀·근무지가 지워진 채널 : 목록에서 숨기고 기록만 남긴다

    private LocalDateTime created;
}
