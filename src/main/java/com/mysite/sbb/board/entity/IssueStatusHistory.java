package com.mysite.sbb.board.entity;

import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 이슈 상태 변경 이력 (이전 상태, 새 상태, 바꾼 사람, 시각, 메모)
// [신뢰성] 상태 변경·이력·알림 이벤트는 한 트랜잭션으로 저장한다 (REL-40)
@Entity
@Table(name = "issue_status_history", indexes = @Index(name = "idx_issue_history_post", columnList = "post_id, id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private IssueStatus fromStatus;         // 처음 등록이면 null

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private IssueStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member changedBy;

    @Column(length = 200)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime changedAt;
}
