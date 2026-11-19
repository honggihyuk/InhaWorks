package com.mysite.sbb.report.entity;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.report.constant.ApprovalDecision;
import com.mysite.sbb.report.constant.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 직원이 제출한 보고서
// [신뢰성] submitToken unique : 제출 버튼 더블클릭 / 새로고침 재전송으로 같은 보고서가 두 번 생기지 않게
// [신뢰성] @Version : 결재자 두 명이 동시에 처리하면 늦은 쪽은 충돌 → @Retryable 로 최신 상태를 다시 읽는다
@Entity
@Getter
@Setter
@ToString(of = {"id", "status", "currentStep"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReportForm form;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    private int currentStep;                // 지금 결재할 단계 (결재선이 없으면 0)

    private LocalDateTime submittedAt;

    private LocalDateTime completedAt;

    @Column(unique = true, length = 36)
    private String submitToken;

    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportAnswer> answers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("step asc")
    private List<ReportApproval> approvals = new ArrayList<>();

    // 지금 차례인 결재 (없으면 null)
    public ReportApproval getCurrentApproval() {
        if (!status.isOpen()) return null;
        return approvals.stream()
                .filter(a -> a.getStep() == currentStep && a.getDecision() == ApprovalDecision.WAITING)
                .findFirst().orElse(null);
    }

    public ReportAnswer getAnswer(Long fieldId) {
        return answers.stream().filter(a -> a.getField().getId().equals(fieldId)).findFirst().orElse(null);
    }
}
