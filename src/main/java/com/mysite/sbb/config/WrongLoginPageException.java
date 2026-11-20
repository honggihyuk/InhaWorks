package com.mysite.sbb.config;

import org.springframework.security.authentication.AccountStatusException;

// 로그인 화면과 계정 종류가 맞지 않을 때 (관리자 계정으로 직원 로그인, 직원 계정으로 관리자 로그인)
//  비밀번호가 맞은 뒤에만 던진다 → 비밀번호를 모르는 사람에게는 계정 종류가 드러나지 않는다
public class WrongLoginPageException extends AccountStatusException {

    public WrongLoginPageException(String msg) {
        super(msg);
    }
}
