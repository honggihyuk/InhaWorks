package com.mysite.sbb.attendance.dto;

import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.attendance.entity.WorkSchedule;
import com.mysite.sbb.attendance.service.WorkPolicyService;
import com.mysite.sbb.member.entity.Member;

import java.util.List;

// 관리자 주간 스케줄 표의 한 줄 : 직원 + 월~일 7칸 (칸마다 근무 여러 개) + 그 주 근로시간(분)
public record WeekRow(Member member, List<List<WorkSchedule>> days, int minutes) {

    public String hoursText() {
        return minutes == 0 ? "-" : WorkPolicy.minutesText(minutes);
    }

    public boolean over52() {
        return minutes > WorkPolicyService.WEEKLY_LIMIT_MINUTES;
    }

    // 1주 소정근로시간을 넘었는지 (연장 근로)
    public boolean overtime() {
        WorkPolicy p = member.getWorkPolicy();
        return p != null && minutes > p.getWeeklyHours() * 60;
    }
}
