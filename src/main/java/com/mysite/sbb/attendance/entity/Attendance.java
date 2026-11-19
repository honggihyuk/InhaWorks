package com.mysite.sbb.attendance.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 출퇴근 기록 : 직원 한 명당 하루 한 줄 (unique : member + workDate)
// [신뢰성] 출근 버튼을 두 번 눌러도 (동시 요청) unique 제약 때문에 두 줄이 생기지 않는다
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_attendance_member_date", columnNames = {"member_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @Column(nullable = false)
    private LocalDate workDate;

    private LocalDateTime clockIn;

    private LocalDateTime clockOut;

    @Version
    private Long version;

    // 일한 시간 (예: 8시간 30분)
    public String getWorkedText() {
        if (clockIn == null || clockOut == null) return "-";
        Duration d = Duration.between(clockIn, clockOut);
        return d.toHours() + "시간 " + d.toMinutesPart() + "분";
    }
}
