package com.mysite.sbb.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// [보안] 대화방에서 빠지거나 퇴사하면 이미 열린 WebSocket 을 닫는다.
//  구독 권한은 구독할 때만 검사하므로, 닫지 않으면 빠진 뒤에도 그 방 메시지를 계속 받게 된다.
//  클라이언트는 자동 재연결하고, 그때 구독 검사에서 걸러진다
@Slf4j
@Component
public class ChatSessionRegistry implements WebSocketHandlerDecoratorFactory {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (session.getPrincipal() != null) {
                    sessions.computeIfAbsent(session.getPrincipal().getName(), k -> ConcurrentHashMap.newKeySet()).add(session);
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                if (session.getPrincipal() != null) {
                    sessions.computeIfPresent(session.getPrincipal().getName(), (k, set) -> {
                        set.remove(session);
                        return set.isEmpty() ? null : set;
                    });
                }
                super.afterConnectionClosed(session, status);
            }
        };
    }

    // 트랜잭션이 커밋된 뒤에 닫는다 (롤백되면 참여 상태가 그대로이므로)
    public void closeAfterCommit(String username) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            close(username);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                close(username);
            }
        });
    }

    private void close(String username) {
        for (WebSocketSession s : sessions.getOrDefault(username, Set.of())) {
            try {
                s.close(CloseStatus.POLICY_VIOLATION.withReason("membership changed"));
            } catch (IOException e) {
                log.warn("=======> WebSocket 닫기 실패 : {}", e.toString());
            }
        }
    }
}
