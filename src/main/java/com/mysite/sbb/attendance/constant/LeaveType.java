package com.mysite.sbb.attendance.constant;

import lombok.Getter;

@Getter
public enum LeaveType {
    ANNUAL("연차"),
    HALF_AM("오전 반차"),
    HALF_PM("오후 반차"),
    SICK("병가"),
    OTHER("기타");

    private final String label;

    LeaveType(String label) {
        this.label = label;
    }
}
