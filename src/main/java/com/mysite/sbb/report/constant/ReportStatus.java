package com.mysite.sbb.report.constant;

import lombok.Getter;

// 보고서 상태 : 제출완료 → (1단계 승인) 검토중 → (마지막 단계 승인) 검토완료, 어느 단계에서든 반려
@Getter
public enum ReportStatus {
    SUBMITTED("제출완료", "st-blue"),
    IN_REVIEW("검토중", "st-orange"),
    APPROVED("검토완료", "st-green"),
    REJECTED("반려", "st-red");

    private final String label;
    private final String css;

    ReportStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public boolean isOpen() {
        return this == SUBMITTED || this == IN_REVIEW;
    }
}
