package com.mysite.sbb.hr.constant;

import lombok.Getter;

// 고용 형태
@Getter
public enum EmploymentType {
    REGULAR("정규직"),
    CONTRACT("계약직"),
    PART_TIME("파트타임"),
    INTERN("인턴");

    private final String label;

    EmploymentType(String label) {
        this.label = label;
    }
}
