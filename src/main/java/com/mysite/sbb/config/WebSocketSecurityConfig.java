package com.mysite.sbb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

// [보안] SEC-04, 07 : STOMP 도 로그인 세션이 있어야 하고, CONNECT 에 CSRF 토큰이 있어야 한다 (@EnableWebSocketSecurity 기본)
//  클라이언트가 보낼 수 있는 것은 정해진 구독뿐 (방 참여자 확인은 ChatSubscriptionInterceptor)
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers("/user/queue/chat", "/topic/rooms/*").authenticated()
                .anyMessage().denyAll()
                .build();
    }
}
