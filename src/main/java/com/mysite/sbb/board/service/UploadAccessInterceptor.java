package com.mysite.sbb.board.service;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

// [보안] SEC-05 : /uploads/** 는 로그인만 하면 열리므로, 팀 전용 게시판 글의 첨부는 주소를 알아도 그 팀만 열 수 있게 막는다
@Component
@RequiredArgsConstructor
public class UploadAccessInterceptor implements HandlerInterceptor {

    private final BoardService boardService;
    private final MemberRepository memberRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Principal principal = request.getUserPrincipal();
        Member me = principal == null ? null : memberRepository.findByUsername(principal.getName()).orElse(null);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (me == null || !boardService.canReadUpload(java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8), me)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
