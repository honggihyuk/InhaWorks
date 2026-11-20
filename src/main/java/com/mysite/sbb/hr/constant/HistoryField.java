package com.mysite.sbb.hr.constant;

import lombok.Getter;

// 인사 변경 이력에 남기는 항목
@Getter
public enum HistoryField {
    TEAM("팀"),
    WORKPLACE("근무지"),
    POSITION("직급"),
    ROLE("권한"),
    WORK_POLICY("근무제"),
    STATUS("재직 상태"),
    EMPLOYMENT("고용 형태"),
    HIRE_DATE("입사일"),
    CONTRACT_END("계약 만료일"),
    RETURN_DATE("복직 예정일"),
    DOCUMENT("인사 서류");

    private final String label;

    HistoryField(String label) {
        this.label = label;
    }
}
