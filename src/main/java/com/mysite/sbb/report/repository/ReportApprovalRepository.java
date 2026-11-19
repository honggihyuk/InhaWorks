package com.mysite.sbb.report.repository;

import com.mysite.sbb.report.constant.ApprovalDecision;
import com.mysite.sbb.report.entity.ReportApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportApprovalRepository extends JpaRepository<ReportApproval, Long> {

    // 결재함 : 나에게 차례가 온 결재 (대기 중이고, 보고서의 현재 단계가 내 단계)
    @Query("select ap from ReportApproval ap join fetch ap.report r " +
            "where ap.approver.id = :memberId and ap.decision = :waiting and ap.step = r.currentStep " +
            "order by ap.id desc")
    List<ReportApproval> findInbox(@Param("memberId") Long memberId, @Param("waiting") ApprovalDecision waiting);

    // 내가 처리한 결재
    List<ReportApproval> findTop30ByApproverIdAndDecisionNotOrderByDecidedAtDesc(Long approverId, ApprovalDecision decision);
}
