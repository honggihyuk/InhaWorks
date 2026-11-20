package com.mysite.sbb.board.constant;

import lombok.Getter;

// 이슈 분류 (이슈 & 해결 게시판)
@Getter
public enum IssueCategory {
    STOCK("재고"),
    FACILITY("시설·장비"),
    CUSTOMER("고객"),
    STAFF("인력"),
    SYSTEM("시스템"),
    SAFETY("안전"),
    ETC("기타");

    private final String label;

    IssueCategory(String label) {
        this.label = label;
    }
}
