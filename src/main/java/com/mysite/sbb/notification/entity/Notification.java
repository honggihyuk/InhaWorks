package com.mysite.sbb.notification.entity;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.notification.constant.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 앱 안 알림 (헤더의 종 모양 + 안 읽은 개수)
// 알림은 업무 트랜잭션에서 바로 만들지 않고, Outbox 이벤트를 처리하면서 만든다 (outbox 패키지 참고).
@Entity
@Table(indexes = @Index(name = "idx_notification_recipient", columnList = "recipient_id, is_read"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member recipient;               // 받는 사람

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationType type;

    @Column(length = 150)
    private String title;

    @Column(length = 500)
    private String message;

    @Column(length = 255)
    private String link;                    // 누르면 이동할 주소 (예: /todo/12)

    @Column(name = "is_read", nullable = false)
    private boolean read;                   // 읽음 (MySQL 예약어 READ 를 피해서 is_read)

    private LocalDateTime created;

    // [신뢰성] 멱등성 키 : "outbox:{이벤트ID}:{받는사람ID}"
    //  같은 Outbox 이벤트가 재시도 등으로 두 번 처리되어도 unique 제약 때문에 알림은 한 번만 만들어진다.
    @Column(unique = true, length = 120)
    private String sourceEventKey;
}
