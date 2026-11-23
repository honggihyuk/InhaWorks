package com.mysite.sbb.chat.controller;

import com.mysite.sbb.chat.service.MemberCardService;
import com.mysite.sbb.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

// 직원 프로필 카드 (조각 HTML) : 사진, 이름, 직급, 근무지, 근무 상태, 근무제, 채팅 버튼
@Controller
@RequiredArgsConstructor
public class MemberCardController {

    private final MemberCardService cardService;
    private final MemberService memberService;

    @GetMapping("/members/{id}/card")
    public String card(@PathVariable("id") Long id, Principal principal, Model model) {
        model.addAttribute("card", cardService.card(id, memberService.getMember(principal.getName()).getId()));
        return "chat/fragments :: memberCard";
    }
}
