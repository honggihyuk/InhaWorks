package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.dto.ChatMessageDto;
import com.mysite.sbb.chat.dto.ChatMessagePayload;
import com.mysite.sbb.chat.entity.ChatMention;
import com.mysite.sbb.chat.entity.ChatMessage;
import com.mysite.sbb.chat.entity.ChatParticipant;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.repository.ChatMentionRepository;
import com.mysite.sbb.chat.repository.ChatMessageRepository;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.common.util.Json;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.notification.service.NotificationService;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.service.OutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

// CHAT_MESSAGE_CREATED 처리기 (REL-21) : ① 멘션 알림 ② 대화방 브로드캐스트 ③ 참여자별 안 읽은 수·미리보기
//  최소 한 번 전달 : 멘션 알림은 sourceEventKey 로 한 번만, 화면은 메시지 id 로 중복을 거른다 (REL-23)
//  일반 메시지는 🔔 알림에 쌓지 않는다. 멘션만 남긴다 (알림을 끈 방이어도) (CHT-23, 37)
@Component
@RequiredArgsConstructor
public class ChatMessageOutboxHandler implements OutboxEventHandler {

    public static final String TYPE = "CHAT_MESSAGE_CREATED";

    private final ChatMessageRepository messageRepository;
    private final ChatMentionRepository mentionRepository;
    private final ChatParticipantRepository participantRepository;
    private final NotificationService notificationService;
    private final ChatAssembler assembler;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        ChatMessagePayload payload = Json.read(event.getPayload(), ChatMessagePayload.class);
        ChatMessage message = messageRepository.findById(payload.messageId()).orElse(null);
        if (message == null) return;    // 이미 없는 메시지 : 다시 시도해도 소용없다
        ChatRoom room = message.getRoom();
        ChatMessageDto dto = assembler.toDto(message);
        List<ChatParticipant> participants = participantRepository.findActive(room.getId());
        Set<Long> activeIds = participants.stream().map(p -> p.getMember().getId()).collect(Collectors.toSet());
        String senderName = dto.sender() == null ? "시스템" : dto.sender().name();
        String preview = ChatMessageWriter.preview(message.getContent() == null ? "" : message.getContent(), dto.attachments().size());

        // ① 멘션 알림 (DB 작업을 먼저 : 실패하면 브로드캐스트 전에 재시도된다)
        Set<Long> mentioned = new HashSet<>();
        for (ChatMention c : mentionRepository.findByMessageIds(List.of(message.getId()))) {
            Member target = c.getMember();
            if (!activeIds.contains(target.getId())) continue;
            mentioned.add(target.getId());
            notificationService.createIfAbsent("chat-mention:" + message.getId() + ":" + target.getId(), target,
                    NotificationType.MENTION, roomTitle(room, senderName) + " 에서 멘션했어요",
                    senderName + " : " + preview, "/chat/rooms/" + room.getId());
        }

        // ② 대화방 브로드캐스트
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kind", "MESSAGE");
        envelope.put("message", dto);
        messagingTemplate.convertAndSend("/topic/rooms/" + room.getId(), (Object) envelope);

        // ③ 참여자별 : 다른 화면 토스트 + 사이드바 안 읽은 수
        Map<Long, Long> totals = new HashMap<>();
        if (!activeIds.isEmpty()) {
            for (Object[] row : participantRepository.countUnreadTotals(activeIds)) totals.put((Long) row[0], (Long) row[1]);
        }
        Long senderId = dto.sender() == null ? null : dto.sender().id();
        for (ChatParticipant p : participants) {
            Member m = p.getMember();
            Map<String, Object> personal = new LinkedHashMap<>();
            personal.put("kind", "NEW");
            personal.put("roomId", room.getId());
            personal.put("roomName", roomTitle(room, senderName));
            personal.put("messageId", message.getId());
            personal.put("senderName", senderName);
            personal.put("preview", preview);
            personal.put("muted", p.isMuted());
            personal.put("mentioned", mentioned.contains(m.getId()));
            personal.put("mine", m.getId().equals(senderId));
            personal.put("unreadTotal", totals.getOrDefault(m.getId(), 0L));
            messagingTemplate.convertAndSendToUser(m.getUsername(), "/queue/chat", (Object) personal);
        }
    }

    private static String roomTitle(ChatRoom room, String senderName) {
        return room.getType() == ChatRoomType.DIRECT ? senderName : room.getName();
    }
}
