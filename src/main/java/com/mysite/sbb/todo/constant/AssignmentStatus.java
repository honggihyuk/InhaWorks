package com.mysite.sbb.todo.constant;

import lombok.Getter;

// 직원별 하루 할 일의 상태
//  NOT_SUBMITTED ─제출─▶ PENDING_REVIEW (검수자가 있을 때) ─통과─▶ APPROVED
//               └─제출─▶ SUBMITTED      (검수자가 없을 때)   └─재작업 요청─▶ REWORK ─다시 제출─▶ PENDING_REVIEW
@Getter
public enum AssignmentStatus {
    NOT_SUBMITTED("제출 미완료", "st-gray"),
    SUBMITTED("제출 완료", "st-blue"),
    PENDING_REVIEW("검수 대기중", "st-orange"),
    APPROVED("검수 통과", "st-green"),
    REWORK("재작업 필요", "st-red");

    private final String label;
    private final String css;

    AssignmentStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    // 직원이 할 일을 끝냈다고 볼 수 있는 상태 (완료율 계산)
    public boolean isDone() {
        return this == SUBMITTED || this == PENDING_REVIEW || this == APPROVED;
    }

    // 직원이 (다시) 제출할 수 있는 상태
    public boolean isSubmittable() {
        return this == NOT_SUBMITTED || this == REWORK;
    }
}
