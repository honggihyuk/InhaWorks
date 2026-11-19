package com.mysite.sbb.outbox.constant;

import lombok.Getter;

// Outbox 이벤트 상태
//   PENDING ──(릴레이가 선점)──▶ PROCESSING ──(성공)──▶ DONE
//      ▲                            │
//      └──(실패, 재시도 남음 : 지수 백오프 후)──┘
//                                   └──(실패, 최대 재시도 초과)──▶ DEAD ──(관리자 수동 재처리)──▶ PENDING
@Getter
public enum OutboxStatus {
    PENDING("대기"),
    PROCESSING("처리 중"),
    DONE("완료"),
    DEAD("실패(DEAD)");

    private final String label;

    OutboxStatus(String label) {
        this.label = label;
    }
}
