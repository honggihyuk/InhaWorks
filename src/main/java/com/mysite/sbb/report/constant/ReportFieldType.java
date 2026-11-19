package com.mysite.sbb.report.constant;

import lombok.Getter;

// 보고서 양식 항목 종류
@Getter
public enum ReportFieldType {
    YES_NO("예/아니오"),
    CHECKBOX("체크박스"),
    IMAGE("이미지"),
    SIGNATURE("서명"),
    CHOICE("객관식"),
    TEXT("주관식"),
    FILE("문서");

    private final String label;

    ReportFieldType(String label) {
        this.label = label;
    }

    public boolean isFileType() {
        return this == IMAGE || this == SIGNATURE || this == FILE;
    }
}
