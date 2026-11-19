package com.mysite.sbb.attendance.entity;

import com.mysite.sbb.attendance.constant.LeaveStatus;
import com.mysite.sbb.attendance.constant.LeaveType;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 휴가 신청 → 관리자 승인 / 반려
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String decisionComment;

    private LocalDateTime created;

    @Version
    private Long version;

    public long getDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
