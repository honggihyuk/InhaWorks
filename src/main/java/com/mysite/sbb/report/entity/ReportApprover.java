package com.mysite.sbb.report.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

// 양식의 결재선 한 단계 (step 1 → 2 → 3 ...)
@Entity
@Getter
@Setter
@ToString(exclude = "form")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportApprover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReportForm form;

    private int step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member approver;
}
