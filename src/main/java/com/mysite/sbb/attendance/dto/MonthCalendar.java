package com.mysite.sbb.attendance.dto;

import com.mysite.sbb.attendance.entity.WorkSchedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // 근무 유형별 묶음 (같은 유형·같은 시각끼리) : 관리자 달력의 색 블록 "오픈 06:00–12:00 · 2명"
        public List<Group> groups() {
            Map<String, Group> map = new LinkedHashMap<>();
            for (WorkSchedule s : schedules) {
                String key = (s.getShiftType() != null ? "t" + s.getShiftType().getId() : "m" + s.getLabel())
                        + "|" + s.getTimeText();
                map.computeIfAbsent(key, k -> new Group(s.getLabel(), s.getTimeText(), s.getToneCss(), s.isOff(), new ArrayList<>()))
                        .schedules().add(s);
            }
            List<Group> list = new ArrayList<>(map.values());
            list.sort((a, b) -> Boolean.compare(a.off(), b.off()));     // 휴무는 아래로
            return list;
        }
    }

    public record Group(String label, String timeText, String toneCss, boolean off, List<WorkSchedule> schedules) {
        public int count() {
            return schedules.size();
        }
    }
}
