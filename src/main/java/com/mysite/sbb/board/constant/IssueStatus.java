package com.mysite.sbb.board.constant;

import lombok.Getter;

@Getter
public enum IssueStatus {
    OPEN("미해결", "st-red"),
    IN_PROGRESS("처리중", "st-orange"),
    RESOLVED("해결", "st-green");

    private final String label;
    private final String css;

    IssueStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }
}
