package com.mysite.sbb.common.constant;

import lombok.Getter;

// 반복 주기 : 할 일(TodoTask) / 보고서 양식(ReportForm) 이 같이 쓴다
@Getter
public enum RepeatCycle {
    DAILY("매일"),
    WEEKLY("매주"),
    MONTHLY("매월"),
    NONE("수시");          // 보고서 : 정해진 주기 없이 필요할 때

    private final String label;

    RepeatCycle(String label) {
        this.label = label;
    }
}
