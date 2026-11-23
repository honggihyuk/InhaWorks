package com.mysite.sbb.board.constant;

import lombok.Getter;

import java.util.List;

// 이슈 처리 단계 (5단계). 예전 값 OPEN 은 시작할 때 REPORTED 로 옮긴다 (IssueStatusMigration)
//  보고 완료 → 검토 중 → (처리 대기 중) → 처리 중 → 해결 완료, 해결 완료 → 검토 중(재오픈)
@Getter
public enum IssueStatus {
    REPORTED("보고 완료", "st-blue"),
    REVIEWING("검토 중", "st-orange"),
    WAITING("처리 대기 중", "st-gray"),
    IN_PROGRESS("처리 중", "st-orange"),
    RESOLVED("해결 완료", "st-green");

    private final String label;
    private final String css;

    IssueStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    // 상태 칩 메뉴에서 고를 수 있는 다음 단계 (requirements.md 7-1 상태도)
    public List<IssueStatus> getNextSteps() {
        return switch (this) {
            case REPORTED -> List.of(REVIEWING);
            case REVIEWING -> List.of(WAITING, IN_PROGRESS, RESOLVED);
            case WAITING -> List.of(IN_PROGRESS);
            case IN_PROGRESS -> List.of(RESOLVED);
            case RESOLVED -> List.of(REVIEWING);
        };
    }

    public boolean canMoveTo(IssueStatus next) {
        return getNextSteps().contains(next);
    }

    // 예전 주소·폼의 OPEN 도 받는다
    public static IssueStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.strip().toUpperCase();
        if ("OPEN".equals(v)) return REPORTED;
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("없는 이슈 상태예요.");
        }
    }
}
