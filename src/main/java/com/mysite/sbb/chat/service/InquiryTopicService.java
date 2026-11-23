package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.dto.InquiryTopicForm;
import com.mysite.sbb.chat.entity.ChatParticipant;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.entity.InquiryTopic;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.chat.repository.ChatRoomRepository;
import com.mysite.sbb.chat.repository.InquiryTopicRepository;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 관리자 : 문의 주제·담당자 관리. 담당자가 바뀌면 그 주제의 문의 방 참여자도 바꾼다
@Service
@RequiredArgsConstructor
@Transactional
public class InquiryTopicService {

    private final InquiryTopicRepository topicRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final ChatSyncService syncService;
    private final ChatSessionRegistry sessionRegistry;

    @Transactional(readOnly = true)
    public List<InquiryTopic> all() {
        return topicRepository.findAllWithHandlers();
    }

    // 직원 화면 : 사용 중이고 재직 담당자가 있는 주제
    @Transactional(readOnly = true)
    public List<InquiryTopic> available() {
        return topicRepository.findAllWithHandlers().stream()
                .filter(t -> t.isActive() && t.getHandlers().stream().anyMatch(Member::isActive)).toList();
    }

    public InquiryTopic save(InquiryTopicForm form) {
        Set<Member> handlers = new LinkedHashSet<>(memberRepository.findAllById(form.getHandlerIds()));
        if (handlers.isEmpty()) throw new BusinessException("담당자를 한 명 이상 골라 주세요.");
        for (Member h : handlers) {
            if (!h.isActive() || (h.getRole() != MemberRole.ADMIN && h.getRole() != MemberRole.MANAGER)) {
                throw new BusinessException("담당자는 재직 중인 관리자·매니저만 될 수 있어요. (" + h.getDisplayName() + ")");
            }
        }
        InquiryTopic topic = form.getId() == null
                ? InquiryTopic.builder().created(LocalDateTime.now()).build()
                : topicRepository.findById(form.getId()).orElseThrow(() -> new EntityNotFoundException("문의 주제를 찾을 수 없어요."));
        Set<Long> before = new HashSet<>();
        topic.getHandlers().forEach(h -> before.add(h.getId()));
        topic.setName(form.getName().strip());
        topic.setDescription(MemberService.emptyToNull(form.getDescription()));
        topic.setIcon(MemberService.emptyToNull(form.getIcon()));
        topic.setActive(form.isActive());
        topic.getHandlers().clear();
        topic.getHandlers().addAll(handlers);
        topic = topicRepository.save(topic);

        Set<Long> after = new HashSet<>();
        handlers.forEach(h -> after.add(h.getId()));
        for (ChatRoom room : roomRepository.findByInquiryTopicId(topic.getId())) {
            room.setName(topic.getName());
            handlers.forEach(h -> syncService.join(room, h));
            for (ChatParticipant p : participantRepository.findByRoomId(room.getId())) {
                Long id = p.getMember().getId();
                boolean asker = room.getInquiryMember() != null && room.getInquiryMember().getId().equals(id);
                if (before.contains(id) && !after.contains(id) && !asker && !p.isLeftRoom()) {
                    p.setLeftRoom(true);
                    sessionRegistry.closeAfterCommit(p.getMember().getUsername());
                }
            }
        }
        return topic;
    }

    public void toggle(Long id) {
        InquiryTopic t = topicRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("문의 주제를 찾을 수 없어요."));
        t.setActive(!t.isActive());
    }
}
