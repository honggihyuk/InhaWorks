package com.mysite.sbb.attendance.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

// 근무 스케줄 (관리자가 배정) : 직원, 날짜, 시작~끝, 근무 유형(선택)
//  휴무 유형이면 시작·끝을 00:00 으로 두고 근로시간 0 으로 센다
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

    @ManyToOne(fetch = FetchType.LAZY)
    private ShiftType shiftType;            // 근무 유형 (없으면 직접 입력한 시간)

    public boolean isOff() {
        return shiftType != null && shiftType.isOff();
    }

    // 화면 이름 : 근무 유형 > 메모 > "근무"
    public String getLabel() {
        if (shiftType != null) return shiftType.getName();
        return memo != null ? memo : "근무";
    }

    public String getTimeText() {
        if (isOff()) return "";
        return startTime + "–" + endTime;
    }

    public String getToneCss() {
        return shiftType != null ? shiftType.getToneOrDefault().getCss() : "tone-blue";
    }

    // 근로시간 (분) : 근무 시간에서 법정 휴게(4시간 이상 30분, 8시간 이상 1시간)를 뺀다. 주 52시간 계산에 쓴다
    public int getWorkMinutes() {
        if (isOff() || startTime == null || endTime == null || !endTime.isAfter(startTime)) return 0;
        int m = (int) Duration.between(startTime, endTime).toMinutes();
        return m - (m >= 480 ? 60 : m >= 240 ? 30 : 0);
    }
}
