package com.mysite.sbb.attendance.dto;

import com.mysite.sbb.attendance.entity.WorkSchedule;
import com.mysite.sbb.member.entity.Member;

import java.util.List;

// 관리자 주간 스케줄 표의 한 줄 : 직원 + 월~일 7칸 (칸마다 근무 여러 개)
public record WeekRow(Member member, List<List<WorkSchedule>> days) {
}
