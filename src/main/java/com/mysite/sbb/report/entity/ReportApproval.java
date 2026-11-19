package com.mysite.sbb.report.entity;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.report.constant.ApprovalDecision;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 보고서 한 건의 결재 한 단계 (제출할 때 양식의 결재선을 복사해 만든다 → 나중에 양식 결재선이 바뀌어도 이미 낸 보고서는 그대로)
@Entity
@Getter
@Setter
@ToString(exclude = "report")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Report report;

    private int step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(name = "decision_comment", length = 500)
    private String comment;

    private LocalDateTime decidedAt;
}
