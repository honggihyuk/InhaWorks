package com.mysite.sbb.common.util;

import com.mysite.sbb.common.constant.RepeatCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 반복 규칙 : 매일 / 매주(요일) / 매월(N일, 없는 날은 말일) + 기간
class RecurrenceRuleTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("매일 : 시작일 전과 종료일 뒤는 제외")
    void daily() {
        LocalDate end = LocalDate.of(2026, 1, 10);
        assertThat(RecurrenceRule.occursOn(RepeatCycle.DAILY, null, null, START, end, LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.DAILY, null, null, START, end, START)).isTrue();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.DAILY, null, null, START, end, end)).isTrue();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.DAILY, null, null, START, end, end.plusDays(1))).isFalse();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.DAILY, null, null, START, null, LocalDate.of(2030, 5, 5))).isTrue();
    }

    @Test
    @DisplayName("매주 : 고른 요일에만")
    void weekly() {
        Set<DayOfWeek> monWed = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(RecurrenceRule.occursOn(RepeatCycle.WEEKLY, monWed, null, START, null, LocalDate.of(2026, 11, 16))).isTrue();  // 월
        assertThat(RecurrenceRule.occursOn(RepeatCycle.WEEKLY, monWed, null, START, null, LocalDate.of(2026, 11, 17))).isFalse(); // 화
        assertThat(RecurrenceRule.occursOn(RepeatCycle.WEEKLY, monWed, null, START, null, LocalDate.of(2026, 11, 18))).isTrue();  // 수
        assertThat(RecurrenceRule.occursOn(RepeatCycle.WEEKLY, Set.of(), null, START, null, LocalDate.of(2026, 11, 16))).isFalse();
    }

    @Test
    @DisplayName("매월 : N일, 그 달에 없으면 마지막 날 (31일 → 2월 28일, 4월 30일)")
    void monthly() {
        assertThat(RecurrenceRule.occursOn(RepeatCycle.MONTHLY, null, 15, START, null, LocalDate.of(2026, 3, 15))).isTrue();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.MONTHLY, null, 15, START, null, LocalDate.of(2026, 3, 16))).isFalse();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.MONTHLY, null, 31, START, null, LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.MONTHLY, null, 31, START, null, LocalDate.of(2026, 4, 30))).isTrue();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.MONTHLY, null, 31, START, null, LocalDate.of(2026, 5, 30))).isFalse();
        assertThat(RecurrenceRule.occursOn(RepeatCycle.MONTHLY, null, 31, START, null, LocalDate.of(2026, 5, 31))).isTrue();
    }

    @Test
    @DisplayName("요일 문자열 ↔ Set 변환, 화면 표시")
    void daysAndDescribe() {
        Set<DayOfWeek> days = RecurrenceRule.parseDays(RecurrenceRule.joinDays(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)));
        assertThat(days).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(RecurrenceRule.describe(RepeatCycle.WEEKLY, days, null)).isEqualTo("매주 월·금");
        assertThat(RecurrenceRule.describe(RepeatCycle.MONTHLY, null, 15)).isEqualTo("매월 15일");
    }
}
