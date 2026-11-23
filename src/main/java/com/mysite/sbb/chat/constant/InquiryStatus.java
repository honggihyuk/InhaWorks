package com.mysite.sbb.chat.constant;

import lombok.Getter;

// 문의 상태 : 직원이 쓰면 미답변, 담당자가 답하면 답변 중, 종료 버튼으로 종료
@Getter
public enum InquiryStatus {
    OPEN("미답변"),
    ANSWERING("답변 중"),
    CLOSED("종료");

    private final String label;

    InquiryStatus(String label) {
        this.label = label;
    }
}
