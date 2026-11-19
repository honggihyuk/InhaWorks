package com.mysite.sbb.common.advice;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.notification.repository.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

// 모든 화면(헤더)에서 공통으로 쓰는 값을 model 에 넣어준다.
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;

    // 로그인한 직원 (헤더의 이름, 프로필 사진)
    @ModelAttribute("loginMember")
    public Member loginMember(Principal principal) {
        if (principal == null) {
            return null;
        }
        return memberRepository.findByUsername(principal.getName()).orElse(null);
    }

    // 지금 주소 (헤더에서 현재 메뉴에 밑줄 표시)
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // 안 읽은 알림 개수 (헤더 🔔 옆 숫자)
    @ModelAttribute("unreadCount")
    public long unreadCount(Principal principal) {
        if (principal == null) {
            return 0;
        }
        return memberRepository.findByUsername(principal.getName())
                .map(m -> notificationRepository.countByRecipientIdAndReadFalse(m.getId()))
                .orElse(0L);
    }
}
