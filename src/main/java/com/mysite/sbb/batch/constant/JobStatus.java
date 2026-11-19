package com.mysite.sbb.batch.constant;

import lombok.Getter;

@Getter
public enum JobStatus {
    RUNNING("실행 중"),
    COMPLETED("완료"),
    FAILED("실패");

    private final String label;

    JobStatus(String label) {
        this.label = label;
    }
}
