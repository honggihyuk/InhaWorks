package com.mysite.sbb.hr.entity;

import com.mysite.sbb.hr.constant.HistoryField;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 인사 변경 이력 : 팀·근무지·직급·권한·근무제·재직 상태·고용 형태·입사/계약/복직일·서류 (바뀔 때 자동 기록)
@Entity
@Table(indexes = @Index(name = "idx_member_history_member", columnList = "member_id, changed_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HistoryField field;

    @Column(length = 100)
    private String beforeValue;

    @Column(length = 100)
    private String afterValue;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;
}
