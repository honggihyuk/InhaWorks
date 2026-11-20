package com.mysite.sbb.hr.dto;

import com.mysite.sbb.hr.entity.HrDocType;
import com.mysite.sbb.hr.entity.HrDocument;
import com.mysite.sbb.member.entity.Member;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 직원 허브(인사 카드) 화면에서 쓰는 묶음들
public final class HrViews {

    private HrViews() {
    }

    // 서류 한 줄 : 종류 + 올린 서류(없으면 null) + 보는 사람 기준 권한
    public record DocRow(HrDocType type, HrDocument doc, boolean canView, boolean canUpload, boolean canDelete) {
        public boolean missing() {
            return doc == null;
        }

        public boolean missingRequired() {
            return doc == null && type.isRequired();
        }
    }

    // 계약 만료 · 복직 예정 알림 (대시보드)
    public record HrAlert(Member member, String label, LocalDate date, long daysLeft) {
    }

    // 근태 탭 : 이번 달 날짜별 상태 + 출근율
    public record AttendanceMonth(YearMonth month, List<List<Day>> weeks, int present, int late, int absent, int leave) {

        // 출근율 = (출근 + 지각) / (출근 + 지각 + 결근). 근무해야 하는 날이 없으면 0
        public int rate() {
            int base = present + late + absent;
            return base == 0 ? 0 : Math.round((present + late) * 100f / base);
        }
    }

    // status : PRESENT 출근, LATE 지각, LEAVE 휴가, ABSENT 결근, OFF 휴무, PLANNED 예정, NONE 기록 없음, OUT 다른 달
    public record Day(LocalDate date, String status, String label, String detail) {
    }
}
