package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.dto.ChatMessageDto;
import com.mysite.sbb.chat.dto.ChatMessageForm;
import com.mysite.sbb.chat.entity.ChatAttachment;
import com.mysite.sbb.chat.entity.ChatMessage;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.repository.ChatAttachmentRepository;
import com.mysite.sbb.chat.repository.ChatMessageRepository;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.chat.repository.ChatRoomRepository;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// 메시지 전송 · 조회 · 읽음
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    public static final int PAGE_SIZE = 50;
    private static final int CATCH_UP_LIMIT = 500;

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final MemberRepository memberRepository;
    private final ChatMessageWriter writer;
    private final ChatAssembler assembler;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.chat.max-files:10}")
    private int maxFiles;

    // [신뢰성] REL-20 : 같은 clientMessageId 는 저장하지 않고 기존 메시지를 돌려준다
    public ChatMessageDto send(Long roomId, Long senderId, ChatMessageForm form, List<MultipartFile> files) {
        List<MultipartFile> real = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (real.size() > maxFiles) throw new BusinessException("파일은 한 번에 " + maxFiles + "개까지 보낼 수 있어요.");

        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        // 파일을 디스크에 쓰기 전에 권한과 중복을 먼저 확인한다
        ChatMessageDto existing = read.execute(s -> {
            ChatRoom room = roomRepository.findById(roomId).orElseThrow(() -> new EntityNotFoundException("대화방을 찾을 수 없어요."));
            Member sender = memberRepository.findById(senderId).orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
            writer.requireCanWrite(room, sender);
            return messageRepository.findByRoomIdAndSenderIdAndClientMessageId(roomId, senderId, form.getClientMessageId())
                    .map(assembler::toDto).orElse(null);
        });
        if (existing != null) return existing;

        try {
            return writer.write(roomId, senderId, form, real);
        } catch (DataIntegrityViolationException e) {
            // 드물게 unique 제약에서 걸린 경우 : 먼저 저장된 메시지를 돌려준다
            entityManager.clear();
            ChatMessageDto dup = read.execute(s -> messageRepository
                    .findByRoomIdAndSenderIdAndClientMessageId(roomId, senderId, form.getClientMessageId())
                    .map(assembler::toDto).orElse(null));
            if (dup == null) throw e;
            return dup;
        }
    }

    // beforeId : 위로 스크롤(이전 50개) / afterId : 재연결 보정(빠진 메시지, id 오름차순) / 둘 다 없으면 최근 50개
    @Transactional(readOnly = true)
    public List<ChatMessageDto> messages(Long roomId, Long memberId, Long beforeId, Long afterId) {
        requireParticipant(roomId, memberId);
        if (afterId != null) {
            return assembler.toDtos(messageRepository.findAfter(roomId, afterId, PageRequest.of(0, CATCH_UP_LIMIT)));
        }
        List<ChatMessage> page = new ArrayList<>(beforeId != null
                ? messageRepository.findBefore(roomId, beforeId, PageRequest.of(0, PAGE_SIZE))
                : messageRepository.findLatest(roomId, PageRequest.of(0, PAGE_SIZE)));
        Collections.reverse(page);
        return assembler.toDtos(page);
    }

    // 읽음 : 앞으로만 간다. 방의 마지막 메시지를 넘는 값은 잘라낸다. 실제로 앞으로 갔으면 그 위치를, 아니면 null
    @Transactional
    public Long markRead(Long roomId, Long memberId, long messageId) {
        requireParticipant(roomId, memberId);
        ChatRoom room = roomRepository.findById(roomId).orElseThrow(() -> new EntityNotFoundException("대화방을 찾을 수 없어요."));
        long last = room.getLastMessageId() == null ? 0 : room.getLastMessageId();
        long target = Math.min(messageId, last);
        if (target <= 0) return null;
        return participantRepository.advanceRead(roomId, memberId, target) > 0 ? target : null;
    }

    @Transactional(readOnly = true)
    public ChatAttachment attachment(Long id, Long memberId) {
        ChatAttachment a = attachmentRepository.findWithRoom(id).orElseThrow(() -> new EntityNotFoundException("파일을 찾을 수 없어요."));
        requireParticipant(a.getMessage().getRoom().getId(), memberId);
        return a;
    }

    private void requireParticipant(Long roomId, Long memberId) {
        if (!participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(roomId, memberId)) {
            throw new NoAccessException("이 대화방에 참여하고 있지 않아요.");
        }
    }
}
