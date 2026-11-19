package com.mysite.sbb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// [보안] 관리자 화면(/admin/**) 접속 IP 제한 (가벼운 버전)
//  app.security.admin-allowed-ips 에 적은 IP(또는 "192.168.0." 처럼 앞부분)로 시작하는 주소만 통과.
//  목록이 비어 있으면 제한하지 않는다. 프록시 뒤에서 운영하면 X-Forwarded-For 처리가 따로 필요하다.
@Slf4j
public class AdminIpAllowFilter extends OncePerRequestFilter {

    private final List<String> allowed;

    public AdminIpAllowFilter(List<String> allowed) {
        this.allowed = allowed == null ? List.of() : allowed.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allowed.isEmpty()) {
            String ip = request.getRemoteAddr();
            boolean ok = allowed.stream().anyMatch(ip::startsWith);
            if (!ok) {
                log.warn("=======> 허용되지 않은 IP 의 관리자 접근 차단 : {} {}", ip, request.getRequestURI());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "허용되지 않은 IP 입니다.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
