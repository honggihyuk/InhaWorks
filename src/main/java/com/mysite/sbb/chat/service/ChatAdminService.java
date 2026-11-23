package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.constant.InquiryStatus;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.repository.ChatMessageRepository;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

// 관리자 채팅 현황 : 채널 목록·통계, 문의함. 1:1 은 건수만 보고 본문·참여자는 보지 않는다 (CHT-52)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAdminService {

    public record ChannelRow(Long id, String type, String typeLabel, String name, long members, long messages, LocalDateTime lastAt) {
    }

    public record InquiryRow(Long id, String topic, Long topicId, String memberName, String status, String statusLabel,
                             String preview, LocalDateTime lastAt) {
    }

    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;

    public List<ChannelRow> channels() {
        List<ChatRoom> rooms = roomRepository.findChannels();
        Map<Long, Long> counts = new HashMap<>();
        if (!rooms.isEmpty()) {
            for (Object[] row : messageRepository.countByRooms(rooms.stream().map(ChatRoom::getId).toList())) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return rooms.stream().map(r -> new ChannelRow(r.getId(), r.getType().name(), r.getType().getLabel(),
                r.getName() == null ? "그룹 대화" : r.getName(), participantRepository.countByRoomIdAndLeftRoomFalse(r.getId()),
                counts.getOrDefault(r.getId(), 0L), r.getLastMessageAt())).toList();
    }

    public long directRooms() {
        return roomRepository.countByType(ChatRoomType.DIRECT);
    }

    public long directMessages() {
        return messageRepository.countDirectMessages();
    }

    public List<InquiryRow> inquiries(Long topicId, InquiryStatus status) {
        return roomRepository.findInquiries().stream()
                .filter(r -> topicId == null || (r.getInquiryTopic() != null && r.getInquiryTopic().getId().equals(topicId)))
                .filter(r -> status == null || r.getInquiryStatus() == status)
                .map(r -> new InquiryRow(r.getId(), r.getInquiryTopic() == null ? "-" : r.getInquiryTopic().getName(),
                        r.getInquiryTopic() == null ? null : r.getInquiryTopic().getId(),
                        r.getInquiryMember() == null ? "-" : r.getInquiryMember().getDisplayName()
                                + (r.getInquiryMember().isActive() ? "" : " (퇴사)"),
                        r.getInquiryStatus() == null ? "OPEN" : r.getInquiryStatus().name(),
                        r.getInquiryStatus() == null ? InquiryStatus.OPEN.getLabel() : r.getInquiryStatus().getLabel(),
                        r.getLastMessagePreview(), r.getLastMessageAt()))
                .toList();
    }

    public long countInquiries(InquiryStatus status) {
        return roomRepository.findByType(ChatRoomType.INQUIRY).stream().filter(r -> r.getInquiryStatus() == status).count();
    }
}
