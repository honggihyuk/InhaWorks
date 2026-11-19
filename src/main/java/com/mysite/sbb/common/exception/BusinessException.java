package com.mysite.sbb.common.exception;

// 화면에 그대로 보여줄 수 있는 업무 오류 (예: "이미 검수가 끝난 할 일이에요")
// GlobalExceptionHandler 가 받아서 안내 화면(message.html)으로 보낸다.
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
