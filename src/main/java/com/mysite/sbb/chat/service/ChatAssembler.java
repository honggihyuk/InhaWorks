package com.mysite.sbb.chat.service;

import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.chat.dto.ChatMessageDto;
import com.mysite.sbb.chat.entity.ChatAttachment;
import com.mysite.sbb.chat.entity.ChatMention;
import com.mysite.sbb.chat.entity.ChatMessage;
import com.mysite.sbb.chat.repository.ChatAttachmentRepository;
import com.mysite.sbb.chat.repository.ChatMentionRepository;
import com.mysite.sbb.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

// 메시지 엔티티 → 화면용 DTO (첨부·멘션은 한 번에 읽는다)
@Component
@RequiredArgsConstructor
public class ChatAssembler {

    private final ChatAttachmentRepository attachmentRepository;
    private final ChatMentionRepository mentionRepository;

    public List<ChatMessageDto> toDtos(List<ChatMessage> messages) {
        if (messages.isEmpty()) return List.of();
        List<Long> ids = messages.stream().map(ChatMessage::getId).toList();
        Map<Long, List<ChatAttachment>> files = attachmentRepository.findByMessageIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getMessage().getId()));
        Map<Long, List<Long>> mentions = new HashMap<>();
        for (ChatMention c : mentionRepository.findByMessageIds(ids)) {
            mentions.computeIfAbsent(c.getMessage().getId(), k -> new ArrayList<>()).add(c.getMember().getId());
        }
        return messages.stream().map(m -> toDto(m, files.getOrDefault(m.getId(), List.of()),
                mentions.getOrDefault(m.getId(), List.of()))).toList();
    }

    public ChatMessageDto toDto(ChatMessage m) {
        return toDtos(List.of(m)).get(0);
    }

    private ChatMessageDto toDto(ChatMessage m, List<ChatAttachment> files, List<Long> mentionIds) {
        return new ChatMessageDto(m.getId(), m.getRoom().getId(), m.getClientMessageId(), m.getType().name(), m.getContent(),
                m.getCreatedAt().toString(), sender(m.getSender()),
                files.stream().map(a -> new ChatMessageDto.Attachment(a.getId(), a.getOriginalName(), a.getSize(), a.isImage(),
                        "/chat/attachments/" + a.getId())).toList(),
                mentionIds);
    }

    private static ChatMessageDto.Sender sender(Member s) {
        if (s == null) return null;
        WorkPolicy policy = s.getWorkPolicy();
        return new ChatMessageDto.Sender(s.getId(), s.getDisplayName(), s.getInitials(), s.getPhotoPath(), !s.isActive(),
                policy == null ? null : policy.getType().getShortLabel(),
                policy == null ? null : policy.getToneOrDefault().getCss());
    }
}
