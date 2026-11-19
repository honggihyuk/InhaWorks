package com.mysite.sbb.attendance.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

// 근무 스케줄 (관리자가 배정) : 직원, 날짜, 시작~끝
@Entity
@Table(indexes = @Index(name = "idx_schedule_date", columnList = "work_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(length = 100)
    private String memo;                    // 예: 오픈조, 마감조

    @ManyToOne(fetch = FetchType.LAZY)
    private Member createdBy;
}
