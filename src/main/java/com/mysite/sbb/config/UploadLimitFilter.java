package com.mysite.sbb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

// [방어] 채팅 첨부(10개 × 20MB) 때문에 multipart 전체 한도를 넓혔으므로, 다른 화면은 예전 한도(한 번에 60MB)를 유지한다
@Component
public class UploadLimitFilter extends OncePerRequestFilter {

    private static final long DEFAULT_LIMIT = 60L * 1024 * 1024;
    private static final Pattern CHAT_SEND = Pattern.compile("^/chat/rooms/\\d+/messages$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        if (type != null && type.toLowerCase().startsWith("multipart/") && request.getContentLengthLong() > DEFAULT_LIMIT) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!CHAT_SEND.matcher(path).matches()) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "한 번에 60MB 까지 올릴 수 있어요.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
