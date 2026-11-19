package com.mysite.sbb.common.util;

import com.mysite.sbb.common.constant.RepeatCycle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// 반복 규칙 : 어떤 날짜에 할 일이 생기는지 계산한다
//  - DAILY   : 시작일 ~ 종료일 매일
//  - WEEKLY  : 고른 요일마다 (예: 월·수·금)
//  - MONTHLY : 매월 N일. 그 달에 N일이 없으면 (예: 2월 31일) 그 달의 마지막 날
//  - NONE    : 시작일 하루만
public final class RecurrenceRule {

    private RecurrenceRule() {}

    public static boolean occursOn(RepeatCycle cycle, Set<DayOfWeek> days, Integer dayOfMonth,
                                   LocalDate start, LocalDate end, LocalDate date) {
        if (cycle == null || start == null || date == null) return false;
        if (date.isBefore(start)) return false;
        if (end != null && date.isAfter(end)) return false;
        return switch (cycle) {
            case DAILY -> true;
            case WEEKLY -> days != null && days.contains(date.getDayOfWeek());
            case MONTHLY -> dayOfMonth != null && dayOfMonth >= 1
                    && date.getDayOfMonth() == Math.min(dayOfMonth, date.lengthOfMonth());
            case NONE -> date.equals(start);
        };
    }

    // "MONDAY,WEDNESDAY" ↔ Set<DayOfWeek>
    public static Set<DayOfWeek> parseDays(String csv) {
        if (csv == null || csv.isBlank()) return EnumSet.noneOf(DayOfWeek.class);
        return Arrays.stream(csv.split(","))
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public static String joinDays(Collection<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return null;
        return EnumSet.copyOf(days).stream().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    // 화면 표시 (예: 매일 / 매주 월·수 / 매월 15일)
    public static String describe(RepeatCycle cycle, Set<DayOfWeek> days, Integer dayOfMonth) {
        if (cycle == null) return "";
        return switch (cycle) {
            case DAILY -> "매일";
            case WEEKLY -> "매주 " + (days == null ? "" : days.stream()
                    .map(d -> d.getDisplayName(TextStyle.SHORT, Locale.KOREAN)).collect(Collectors.joining("·")));
            case MONTHLY -> "매월 " + dayOfMonth + "일";
            case NONE -> "한 번";
        };
    }
}
