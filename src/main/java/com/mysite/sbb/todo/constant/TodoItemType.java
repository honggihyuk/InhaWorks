package com.mysite.sbb.todo.constant;

import lombok.Getter;

// 할 일 항목 종류
@Getter
public enum TodoItemType {
    YES_NO("예/아니오"),
    CHECKBOX("체크박스"),
    PHOTO("사진"),
    SIGNATURE("서명"),
    CHOICE("객관식"),
    TEXT("주관식");

    private final String label;

    TodoItemType(String label) {
        this.label = label;
    }
}
