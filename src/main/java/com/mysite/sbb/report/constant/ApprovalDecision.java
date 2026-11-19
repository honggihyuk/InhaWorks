package com.mysite.sbb.report.constant;

import lombok.Getter;

@Getter
public enum ApprovalDecision {
    WAITING("대기", "st-gray"),
    APPROVED("승인", "st-green"),
    REJECTED("반려", "st-red");

    private final String label;
    private final String css;

    ApprovalDecision(String label, String css) {
        this.label = label;
        this.css = css;
    }
}
