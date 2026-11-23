package com.mysite.sbb.common.exception;

// 볼 권한이 없을 때 (403 안내 화면). 예) 다른 팀 전용 게시판의 이슈 링크, 참여하지 않은 대화방
public class NoAccessException extends BusinessException {

    public NoAccessException(String message) {
        super(message);
    }
}
