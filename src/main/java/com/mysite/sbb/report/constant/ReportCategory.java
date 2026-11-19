package com.mysite.sbb.report.constant;

import lombok.Getter;

// 보고서 분류 (목록에서 고르기) + 해시태그(#주간보고)로 한 번 더 나눈다
@Getter
public enum ReportCategory {
    TEAM("팀별"),
    TOPIC("주제별"),
    WORKPLACE("매장별");

    private final String label;

    ReportCategory(String label) {
        this.label = label;
    }
}
