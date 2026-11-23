package com.mysite.sbb.config;

import com.mysite.sbb.chat.service.ChatRoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// [보안] SEC-04 : /topic/rooms/{id} 구독은 그 방의 현재 참여자만
@Slf4j
@Component
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM = Pattern.compile("^/topic/rooms/(\\d{1,18})$");

    private final ObjectProvider<ChatRoomService> roomService;

    public ChatSubscriptionInterceptor(ObjectProvider<ChatRoomService> roomService) {
        this.roomService = roomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) return message;
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/")) return message;
        Matcher m = ROOM.matcher(destination);
        Principal user = accessor.getUser();
        if (!m.matches() || user == null || !roomService.getObject().isParticipant(Long.valueOf(m.group(1)), user.getName())) {
            log.warn("=======> 대화방 구독 거부 : {} → {}", user == null ? "(없음)" : user.getName(), destination);
            throw new AccessDeniedException("이 대화방에 참여하고 있지 않아요.");
        }
        return message;
    }
}
