package com.mysite.sbb.attendance.entity;

import com.mysite.sbb.attendance.constant.Tone;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalTime;

// 근무 유형 (근무 템플릿) : 근무 정책에 속한다. 예) 오픈 06:00~12:00, 마감 17:00~22:00, 사무실 근무 09:00~18:00, 휴무
//  스케줄을 배정할 때 직원의 근무 정책에 있는 유형만 제안하고, 달력에서는 유형별 색 블록으로 묶어 보여 준다
@Entity
@Getter
@Setter
@ToString(exclude = "policy")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private WorkPolicy policy;

    @Column(nullable = false, length = 30)
    private String name;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(name = "off_day", nullable = false)
    private boolean off;                    // 휴무 (시간 없음, 근로시간 0)

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Tone tone;

    @Column(nullable = false)
    private int sortOrder;

    public Tone getToneOrDefault() {
        return tone != null ? tone : (off ? Tone.GREY : Tone.BLUE);
    }

    public String getTimeText() {
        if (off || startTime == null || endTime == null) return "";
        return startTime + "–" + endTime;
    }

    public int getMinutes() {
        if (off || startTime == null || endTime == null || !endTime.isAfter(startTime)) return 0;
        return (int) Duration.between(startTime, endTime).toMinutes();
    }
}
