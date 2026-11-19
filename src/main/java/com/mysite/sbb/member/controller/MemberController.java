package com.mysite.sbb.member.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 직원 로그인 화면
// 사내 시스템이라 회원가입은 없고, 계정은 관리자가 만든다 (관리자 모드 > 직원 관리)
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/member")
public class MemberController {

    // 로그인 화면 (POST /member/login 은 Spring Security 가 처리하므로 GET 만 만든다)
    @GetMapping("/login")
    public String login() {
        return "member/login";
    }
}
