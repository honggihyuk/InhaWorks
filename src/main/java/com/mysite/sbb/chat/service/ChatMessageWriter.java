package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.constant.ChatMessageType;
import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.constant.InquiryStatus;
import com.mysite.sbb.chat.dto.ChatMessageDto;
import com.mysite.sbb.chat.dto.ChatMessageForm;
import com.mysite.sbb.chat.dto.ChatMessagePayload;
import com.mysite.sbb.chat.entity.*;
import com.mysite.sbb.chat.repository.*;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.outbox.service.OutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 메시지 저장 트랜잭션 : 메시지 + 첨부 + 멘션 + 방 요약 + 내 읽음 위치 + Outbox 이벤트를 함께 커밋한다 (REL-21)
@Service
@RequiredArgsConstructor
public class ChatMessageWriter {

    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_.\\-]{2,30})");

    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatMentionRepository mentionRepository;
    private final MemberRepository memberRepository;
    private final FileStore fileStore;
    private final OutboxService outboxService;
    private final ChatAssembler assembler;

    @Transactional
    public ChatMessageDto write(Long roomId, Long senderId, ChatMessageForm form, List<MultipartFile> files) {
        // 파일은 방 잠금 전에 저장한다 (큰 파일 때문에 그 방의 다른 전송이 기다리지 않게). 롤백되면 FileStore 가 지운다
        List<FileRef> stored = new ArrayList<>();
        for (MultipartFile f : files) {
            FileRef ref = fileStore.storeChatFile(f);
            if (ref != null) stored.add(ref);
        }

        ChatRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new EntityNotFoundException("대화방을 찾을 수 없어요."));
        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
        requireCanWrite(room, sender);

        // [신뢰성] 잠금 뒤 한 번 더 확인 : 같은 키가 동시에 와도 하나만 저장된다
        Optional<ChatMessage> dup = messageRepository.findByRoomIdAndSenderIdAndClientMessageId(roomId, senderId, form.getClientMessageId());
        if (dup.isPresent()) {
            stored.forEach(f -> fileStore.deletePrivateAfterCommit(f.getPath()));
            return assembler.toDto(dup.get());
        }

        String content = form.getContent() == null ? "" : form.getContent().strip();
        if (content.isEmpty() && stored.isEmpty()) throw new BusinessException("메시지를 입력하거나 파일을 첨부해 주세요.");

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = messageRepository.save(ChatMessage.builder().room(room).sender(sender)
                .clientMessageId(form.getClientMessageId())
                .type(content.isEmpty() ? ChatMessageType.FILE : ChatMessageType.TEXT)
                .content(content).createdAt(now).build());
        for (FileRef f : stored) {
            attachmentRepository.save(ChatAttachment.builder().message(message).originalName(f.getOriginalName())
                    .storedName(f.getPath()).size(f.getSize() == null ? 0 : f.getSize()).image(f.isImage()).build());
        }
        for (Member m : mentioned(room, sender, content, form.getMentionIds())) {
            mentionRepository.save(ChatMention.builder().message(message).member(m).build());
        }

        room.setLastMessageId(message.getId());
        room.setLastMessageAt(now);
        room.setLastMessagePreview(preview(content, stored.size()));
        if (room.getType() == ChatRoomType.INQUIRY) {
            boolean asker = room.getInquiryMember() != null && room.getInquiryMember().getId().equals(senderId);
            room.setInquiryStatus(asker ? InquiryStatus.OPEN : InquiryStatus.ANSWERING);
        }
        participantRepository.advanceRead(roomId, senderId, message.getId());

        outboxService.publish(ChatMessageOutboxHandler.TYPE, "ChatMessage", message.getId(),
                new ChatMessagePayload(message.getId()), "chat-message:" + message.getId());
        return assembler.toDto(message);
    }

    // 쓸 수 있는지 : 참여 중 + 공지는 관리자만 + 1:1 상대가 퇴사했으면 불가
    public void requireCanWrite(ChatRoom room, Member sender) {
        if (room.isArchived()) throw new BusinessException("보관된 대화방에는 쓸 수 없어요.");
        if (!sender.isActive() || !participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(room.getId(), sender.getId())) {
            throw new NoAccessException("이 대화방에 참여하고 있지 않아요.");
        }
        if (room.getType() == ChatRoomType.NOTICE && !sender.isAdmin()) {
            throw new NoAccessException("전체 공지는 관리자만 쓸 수 있어요.");
        }
        if (room.getType() == ChatRoomType.DIRECT) {
            boolean partnerGone = participantRepository.findByRoomId(room.getId()).stream()
                    .anyMatch(p -> !p.getMember().getId().equals(sender.getId()) && !p.getMember().isActive());
            if (partnerGone) throw new BusinessException("퇴사한 직원에게는 메시지를 보낼 수 없어요.");
        }
    }

    // 멘션 : 자동완성으로 고른 사람(본문에 @이름 이 있어야 함) + 본문의 @아이디. 이 방 참여자만, 나는 빼고
    private List<Member> mentioned(ChatRoom room, Member sender, String content, List<Long> picked) {
        if (content.isEmpty() || !content.contains("@")) return List.of();
        Set<String> usernames = new HashSet<>();
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find()) usernames.add(matcher.group(1));
        Set<Long> pickedIds = picked == null ? Set.of() : new HashSet<>(picked);
        List<Member> result = new ArrayList<>();
        for (ChatParticipant p : participantRepository.findActive(room.getId())) {
            Member m = p.getMember();
            if (m.getId().equals(sender.getId())) continue;
            boolean byPick = pickedIds.contains(m.getId()) && content.contains("@" + m.getDisplayName());
            if (byPick || usernames.contains(m.getUsername())) result.add(m);
        }
        return result;
    }

    static String preview(String content, int fileCount) {
        String text = content.isEmpty() ? "📎 파일 " + fileCount + "개" : content.replaceAll("\\s+", " ");
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }
}
