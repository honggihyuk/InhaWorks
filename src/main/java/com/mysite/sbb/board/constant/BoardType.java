package com.mysite.sbb.board.constant;

import lombok.Getter;

@Getter
public enum BoardType {
    GENERAL("일반"),
    ISSUE("이슈 & 해결");      // 글마다 상태(미해결/처리중/해결) + 해결 방법

    private final String label;

    BoardType(String label) {
        this.label = label;
    }
}
