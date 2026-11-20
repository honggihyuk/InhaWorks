package com.mysite.sbb.attendance.entity;

import com.mysite.sbb.attendance.constant.Tone;
import com.mysite.sbb.attendance.constant.WorkPolicyType;
import com.mysite.sbb.organization.entity.Workplace;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 근무 정책 (근무제) : 직원마다 하나씩 배정한다 (Member.workPolicy)
//  1주 소정근로시간, 근무 요일, 기본 출퇴근 시각, 휴게시간, 근무지, 그리고 이 정책에서 쓰는 근무 유형(ShiftType) 목록
@Entity
@Getter
@Setter
@ToString(exclude = {"workplace", "shiftTypes"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkPolicy {

    public static final List<DayOfWeek> WEEK = List.of(DayOfWeek.values());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;                    // 예: 본사 시차 출퇴근

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkPolicyType type;

    @Builder.Default
    @Column(nullable = false)
    private int weeklyHours = 40;           // 1주 소정근로시간

    @Builder.Default
    @Column(length = 30)
    private String workDays = "MON,TUE,WED,THU,FRI";   // 근무 요일 (DayOfWeek 이름, 쉼표로)

    private LocalTime startTime;            // 기본 출근 (재량·간주 근무제는 비워도 된다)

    private LocalTime endTime;              // 기본 퇴근

    @Builder.Default
    @Column(nullable = false)
    private int breakMinutes = 60;          // 휴게시간 (분)

    @ManyToOne(fetch = FetchType.LAZY)
    private Workplace workplace;            // 근무지 (선택)

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Tone tone;

    @Builder.Default
    @Column(nullable = false)
    private int sortOrder = 0;

    private LocalDateTime created;

    @Builder.Default
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ShiftType> shiftTypes = new ArrayList<>();

    public void addShiftType(ShiftType s) {
        s.setPolicy(this);
        s.setSortOrder(shiftTypes.size());
        shiftTypes.add(s);
    }

    public Tone getToneOrDefault() {
        return tone != null ? tone : type.getTone();
    }

    public List<DayOfWeek> getWorkDayList() {
        if (workDays == null || workDays.isBlank()) return List.of();
        return Arrays.stream(workDays.split(",")).map(String::strip).filter(s -> s.length() >= 3)
                .map(WorkPolicy::parseDay).filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    // "MON" · "MONDAY" 둘 다 받는다
    private static DayOfWeek parseDay(String s) {
        String key = s.toUpperCase().substring(0, 3);
        for (DayOfWeek d : DayOfWeek.values()) if (d.name().startsWith(key)) return d;
        return null;
    }

    public boolean worksOn(DayOfWeek d) {
        return getWorkDayList().contains(d);
    }

    // "월 화 수 목 금"
    public String getWorkDaysText() {
        List<DayOfWeek> days = getWorkDayList();
        if (days.isEmpty()) return "정하지 않음";
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : days) sb.append(sb.isEmpty() ? "" : " ").append(dayLabel(d));
        return sb.toString();
    }

    // "09:00 ~ 18:00" (시각이 없으면 "자율")
    public String getTimeText() {
        if (startTime == null) return "자율";
        return startTime + " ~ " + (endTime == null ? "" : endTime);
    }

    // 하루 소정근로시간 = (퇴근 - 출근) - 휴게. 시각이 없으면 1주 소정 / 근무일 수
    public int getDailyMinutes() {
        if (startTime != null && endTime != null && endTime.isAfter(startTime)) {
            return (int) Math.max(0, Duration.between(startTime, endTime).toMinutes() - breakMinutes);
        }
        int days = getWorkDayList().size();
        return days == 0 ? 0 : weeklyHours * 60 / days;
    }

    // 하루 소정근로 : 출퇴근 시각이 없으면 스케줄 근무제는 "근무 유형별", 나머지는 "자율"
    public String getDailyText() {
        if (startTime == null || endTime == null) return type == WorkPolicyType.SHIFT ? "근무 유형별" : "자율";
        return minutesText(getDailyMinutes());
    }

    public String getBreakText() {
        return breakMinutes == 0 ? "없음" : minutesText(breakMinutes);
    }

    public static String dayLabel(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    // 450 → "7시간 30분", 480 → "8시간"
    public static String minutesText(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "분";
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }
}
