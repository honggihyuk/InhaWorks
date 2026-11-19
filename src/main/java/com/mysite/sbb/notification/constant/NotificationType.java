package com.mysite.sbb.notification.constant;

import lombok.Getter;

// 알림 종류 (알림 목록의 작은 태그)
@Getter
public enum NotificationType {
    TODO("할 일"),
    REPORT("보고서"),
    APPROVAL("결재"),
    BOARD("게시판"),
    ATTENDANCE("근태"),
    MENTION("멘션"),
    SYSTEM("시스템");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }
}
