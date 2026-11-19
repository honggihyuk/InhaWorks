package com.mysite.sbb.admin.dto;

import java.time.LocalDate;
import java.util.List;

// 관리자 대시보드 (읽기 전용 집계)
//  hero : 오늘 할 일 완료율, 제출한 직원 수 / 배정된 직원 수
//  KPI  : 검수 대기, 결재 대기 보고서
//  근무지별 업무 현황, 이번 주 제출, 확인 필요(DEAD 이벤트·휴가 신청)
public record AdminDashboard(LocalDate today,
                             long total,
                             long done,
                             int rate,
                             long members,
                             long membersDone,
                             long reviewWaiting,
                             long approvalWaiting,
                             List<WorkplaceRow> workplaces,
                             List<DayBar> week,
                             long weekMax,
                             long dead,
                             long leavePending) {

    public long membersLeft() {
        return members - membersDone;
    }

    // 근무지 한 줄 : 배정 → 제출 완료 → 검수 대기 / 재작업 필요 / 검수 통과
    public record WorkplaceRow(String name, long assigned, long done, long reviewWaiting, long rework, long approved) {

        public int rate() {
            return assigned == 0 ? 0 : (int) Math.round(done * 100.0 / assigned);
        }

        public int pct(long value) {
            return assigned == 0 ? 0 : (int) Math.round(value * 100.0 / assigned);
        }
    }

    // 이번 주 하루치 제출 수 (막대 그래프)
    public record DayBar(LocalDate date, long count, boolean today) {
    }
}
