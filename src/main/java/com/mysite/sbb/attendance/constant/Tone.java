package com.mysite.sbb.attendance.constant;

import lombok.Getter;

// 근무제·근무 유형 칩의 연한 파스텔 색 (app.css 의 .tone-*)
@Getter
public enum Tone {
    BLUE("파랑"),
    TEAL("청록"),
    GREEN("초록"),
    YELLOW("노랑"),
    ORANGE("주황"),
    RED("빨강"),
    PINK("분홍"),
    PURPLE("보라"),
    GREY("회색");

    private final String label;

    Tone(String label) {
        this.label = label;
    }

    public String getCss() {
        return "tone-" + name().toLowerCase();
    }
}
