package com.mysite.sbb.notification.controller;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

// 알림 목록 (헤더의 🔔)
@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final MemberService memberService;
    private final NotificationService notificationService;

    @GetMapping
    public String list(Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        model.addAttribute("notificationList", notificationService.getList(me));
        return "notification/list";
    }

    // 알림을 누르면 읽음 처리하고 해당 화면으로 이동
    @PostMapping("/{id}/read")
    public String read(@PathVariable("id") Long id, Principal principal) {
        String link = notificationService.read(id, memberService.getMember(principal.getName()));
        return "redirect:" + link;
    }

    @PostMapping("/read-all")
    public String readAll(Principal principal) {
        notificationService.readAll(memberService.getMember(principal.getName()));
        return "redirect:/notifications";
    }
}
