package com.mysite.sbb.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 시작할 때 매장·팀·공지 채널과 참여자를 맞춘다 (DataInitializer 가 예시 데이터를 만든 뒤)
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ChatStartupSync implements CommandLineRunner {

    private final ChatSyncService syncService;

    @Override
    public void run(String... args) {
        syncService.syncAll();
        log.info("=======> 채팅 채널·참여자 동기화 완료");
    }
}
