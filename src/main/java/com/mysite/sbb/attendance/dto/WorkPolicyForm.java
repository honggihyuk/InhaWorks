package com.mysite.sbb.attendance.dto;

import com.mysite.sbb.attendance.constant.Tone;
import com.mysite.sbb.attendance.constant.WorkPolicyType;
import com.mysite.sbb.attendance.entity.WorkPolicy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// 근무 정책 만들기 / 고치기 폼
@Getter
@Setter
public class WorkPolicyForm {

    private String name;
    private WorkPolicyType type = WorkPolicyType.FIXED;
    private int weeklyHours = 40;
    private List<DayOfWeek> workDays = new ArrayList<>(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime = LocalTime.of(9, 0);
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime = LocalTime.of(18, 0);
    private int breakMinutes = 60;
    private Long workplaceId;
    private Tone tone;

    public static WorkPolicyForm of(WorkPolicy p) {
        WorkPolicyForm f = new WorkPolicyForm();
        f.setName(p.getName());
        f.setType(p.getType());
        f.setWeeklyHours(p.getWeeklyHours());
        f.setWorkDays(new ArrayList<>(p.getWorkDayList()));
        f.setStartTime(p.getStartTime());
        f.setEndTime(p.getEndTime());
        f.setBreakMinutes(p.getBreakMinutes());
        f.setWorkplaceId(p.getWorkplace() == null ? null : p.getWorkplace().getId());
        f.setTone(p.getTone());
        return f;
    }
}
