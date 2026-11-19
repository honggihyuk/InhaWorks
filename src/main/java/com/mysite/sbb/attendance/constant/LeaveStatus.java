package com.mysite.sbb.attendance.constant;

import lombok.Getter;

@Getter
public enum LeaveStatus {
    REQUESTED("승인 대기", "st-orange"),
    APPROVED("승인", "st-green"),
    REJECTED("반려", "st-red"),
    CANCELED("취소", "st-gray");

    private final String label;
    private final String css;

    LeaveStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }
}
