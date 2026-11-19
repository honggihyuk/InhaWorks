package com.mysite.sbb.attendance.dto;

import com.mysite.sbb.attendance.entity.WorkSchedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 월별 스케줄 달력 : 월요일부터 시작하는 주(7칸) 여러 줄
//  달력 앞뒤로 지난달·다음달 날짜도 채운다 (inMonth = false 로 흐리게)
public record MonthCalendar(YearMonth month, List<List<Day>> weeks, int total) {

    public YearMonth prev() {
        return month.minusMonths(1);
    }

    public YearMonth next() {
        return month.plusMonths(1);
    }

    // 하루 칸
    public record Day(LocalDate date, boolean inMonth, boolean today, List<WorkSchedule> schedules) {
    }
}
