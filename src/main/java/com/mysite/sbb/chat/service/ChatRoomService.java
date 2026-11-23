package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.constant.InquiryStatus;
import com.mysite.sbb.chat.dto.ChatRoomRow;
import com.mysite.sbb.chat.dto.ChatRoomView;
import com.mysite.sbb.chat.entity.ChatParticipant;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.entity.InquiryTopic;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.chat.repository.ChatRoomRepository;
import com.mysite.sbb.chat.repository.InquiryTopicRepository;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// 대화방 : 목록, 1:1 · 그룹 · 문의 방 열기, 알림 끄기, 나가기, 문의 종료
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final InquiryTopicRepository topicRepository;
    private final MemberRepository memberRepository;
    private final ChatSyncService syncService;
    private final ChatSessionRegistry sessionRegistry;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    // ---------- 목록 ----------

    @Transactional(readOnly = true)
    public List<ChatRoomRow> rooms(Long meId, String query) {
        List<ChatParticipant> mine = participantRepository.findMine(meId);
        if (mine.isEmpty()) return List.of();
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : participantRepository.countUnreadByRoom(meId)) unread.put((Long) row[0], (Long) row[1]);
        Map<Long, List<Member>> members = new HashMap<>();
        for (ChatParticipant p : participantRepository.findActiveInRooms(mine.stream().map(p -> p.getRoom().getId()).toList())) {
            members.computeIfAbsent(p.getRoom().getId(), k -> new ArrayList<>()).add(p.getMember());
        }
        String q = query == null ? "" : query.strip().toLowerCase();
        return mine.stream().map(p -> row(p, meId, members.getOrDefault(p.getRoom().getId(), List.of()),
                        unread.getOrDefault(p.getRoom().getId(), 0L)))
                .filter(r -> q.isEmpty() || r.name().toLowerCase().contains(q))
                .sorted(Comparator.comparing(ChatRoomRow::lastMessageAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()
                        .thenComparing(ChatRoomRow::id))
                .toList();
    }

    private ChatRoomRow row(ChatParticipant p, Long meId, List<Member> people, long unread) {
        ChatRoom r = p.getRoom();
        List<Member> others = people.stream().filter(m -> !m.getId().equals(meId)).toList();
        Member partner = r.getType() == ChatRoomType.DIRECT && !others.isEmpty() ? others.get(0) : null;
        return new ChatRoomRow(r.getId(), r.getType().name(), r.getType().getLabel(), displayName(r, meId, others),
                others.stream().limit(3).toList(), people.size(), r.getLastMessagePreview(), r.getLastMessageAt(), unread,
                p.isMuted(), r.getType().isLeavable(), r.getInquiryStatus() == null ? null : r.getInquiryStatus().getLabel(), partner);
    }

    private String displayName(ChatRoom r, Long meId, List<Member> others) {
        if (r.getType() == ChatRoomType.DIRECT) {
            if (!others.isEmpty()) return others.get(0).getDisplayName();
            // 상대가 나갔거나 퇴사한 1:1 : 참여 기록에서 이름을 찾는다
            return participantRepository.findByRoomId(r.getId()).stream().filter(x -> !x.getMember().getId().equals(meId))
                    .findFirst().map(x -> x.getMember().getDisplayName() + (x.getMember().isActive() ? "" : " (퇴사)")).orElse("대화 상대 없음");
        }
        if (r.getType() == ChatRoomType.INQUIRY && r.getInquiryMember() != null && !r.getInquiryMember().getId().equals(meId)) {
            return r.getName() + " · " + r.getInquiryMember().getDisplayName();
        }
        if (r.getType() == ChatRoomType.GROUP && (r.getName() == null || r.getName().isBlank())) {
            String names = others.stream().limit(3).map(Member::getDisplayName).collect(Collectors.joining(", "));
            return names.isEmpty() ? "그룹 대화" : names;
        }
        return r.getName();
    }

    @Transactional(readOnly = true)
    public long unreadTotal(Long meId) {
        return participantRepository.countUnreadTotals(List.of(meId)).stream().mapToLong(row -> (Long) row[1]).sum();
    }

    @Transactional(readOnly = true)
    public ChatRoomView view(Long roomId, Long meId) {
        ChatParticipant me = myRow(roomId, meId);
        ChatRoom r = me.getRoom();
        Member member = me.getMember();
        List<ChatParticipant> active = participantRepository.findActive(roomId);
        List<Member> others = active.stream().map(ChatParticipant::getMember).filter(m -> !m.getId().equals(meId)).toList();
        boolean partnerGone = r.getType() == ChatRoomType.DIRECT && participantRepository.findByRoomId(roomId).stream()
                .anyMatch(p -> !p.getMember().getId().equals(meId) && !p.getMember().isActive());
        boolean canWrite = !partnerGone && (r.getType() != ChatRoomType.NOTICE || member.isAdmin());
        boolean handler = r.getType() == ChatRoomType.INQUIRY && isHandler(r, member);
        return new ChatRoomView(r.getId(), r.getType().name(), r.getType().getLabel(), displayName(r, meId, others), canWrite, me.isMuted(),
                r.getType().isLeavable() && !handler, handler && r.getInquiryStatus() != InquiryStatus.CLOSED,
                r.getInquiryStatus() == null ? null : r.getInquiryStatus().getLabel(), me.getLastReadMessageId(),
                active.stream().map(p -> {
                    Member m = p.getMember();
                    return new ChatRoomView.Person(m.getId(), m.getDisplayName(), m.getInitials(), m.getPhotoPath(), m.getSubtitle(),
                            p.getLastReadMessageId(), m.getWorkPolicy() == null ? null : m.getWorkPolicy().getType().getShortLabel(),
                            m.getWorkPolicy() == null ? null : m.getWorkPolicy().getToneOrDefault().getCss());
                }).toList());
    }

    private boolean isHandler(ChatRoom r, Member m) {
        return m.isAdmin() || (r.getInquiryTopic() != null
                && r.getInquiryTopic().getHandlers().stream().anyMatch(h -> h.getId().equals(m.getId())));
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(Long roomId, String username) {
        return memberRepository.findByUsername(username)
                .filter(Member::isActive)
                .map(m -> participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(roomId, m.getId()))
                .orElse(false);
    }

    private ChatParticipant myRow(Long roomId, Long meId) {
        ChatParticipant p = participantRepository.findByRoomIdAndMemberId(roomId, meId)
                .filter(x -> !x.isLeftRoom() && !x.getRoom().isArchived())
                .orElse(null);
        if (p == null) {
            if (!roomRepository.existsById(roomId)) throw new EntityNotFoundException("대화방을 찾을 수 없어요.");
            throw new NoAccessException("이 대화방에 참여하고 있지 않아요.");
        }
        return p;
    }

    // ---------- 방 열기 (같은 방은 하나만 : unique 키 + 동시 요청이면 먼저 만든 방) ----------

    public Long openDirect(Long meId, Long otherId) {
        if (meId.equals(otherId)) throw new BusinessException("자기 자신과는 대화할 수 없어요.");
        String key = Math.min(meId, otherId) + "_" + Math.max(meId, otherId);
        return createOnce(() -> {
            Member me = active(meId);
            Member other = active(otherId);
            ChatRoom room = roomRepository.findByDirectKey(key).orElseGet(() -> roomRepository.save(ChatRoom.builder()
                    .type(ChatRoomType.DIRECT).directKey(key).created(LocalDateTime.now()).build()));
            syncService.join(room, me);     // 내가 나갔던 방이면 다시 들어온다
            if (participantRepository.findByRoomIdAndMemberId(room.getId(), otherId).isEmpty()) syncService.join(room, other);
            return room.getId();
        }, () -> roomRepository.findByDirectKey(key).map(ChatRoom::getId));
    }

    @Transactional
    public Long createGroup(Long meId, String name, List<Long> memberIds) {
        Set<Long> ids = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        ids.remove(meId);
        if (ids.size() < 2) throw new BusinessException("그룹 대화는 나 말고 두 명 이상을 골라 주세요.");
        if (ids.size() > 100) throw new BusinessException("그룹 대화는 100명까지예요.");
        String title = name == null ? "" : name.strip();
        if (title.length() > 80) throw new BusinessException("대화방 이름은 80자까지예요.");
        ChatRoom room = roomRepository.save(ChatRoom.builder().type(ChatRoomType.GROUP)
                .name(title.isEmpty() ? null : title).created(LocalDateTime.now()).build());
        syncService.join(room, active(meId));
        for (Long id : ids) syncService.join(room, active(id));
        return room.getId();
    }

    // 문의 방 : 직원 + 그 주제 담당자만. 직원·주제마다 하나
    public Long openInquiry(Long meId, Long topicId) {
        String key = "I:" + topicId + ":" + meId;
        return createOnce(() -> {
            Member me = active(meId);
            InquiryTopic topic = topicRepository.findById(topicId).filter(InquiryTopic::isActive)
                    .orElseThrow(() -> new EntityNotFoundException("문의 주제를 찾을 수 없어요."));
            List<Member> handlers = topic.getHandlers().stream().filter(Member::isActive).toList();
            if (handlers.isEmpty()) throw new BusinessException("이 주제의 담당자가 아직 없어요.");
            ChatRoom room = roomRepository.findByChannelKey(key).orElseGet(() -> roomRepository.save(ChatRoom.builder()
                    .type(ChatRoomType.INQUIRY).name(topic.getName()).channelKey(key).inquiryTopic(topic).inquiryMember(me)
                    .inquiryStatus(InquiryStatus.OPEN).created(LocalDateTime.now()).build()));
            syncService.join(room, me);
            handlers.forEach(h -> syncService.join(room, h));
            return room.getId();
        }, () -> roomRepository.findByChannelKey(key).map(ChatRoom::getId));
    }

    // 관리자 문의함에서 열기 : 담당 주제가 아니어도 관리자는 참여해서 응대한다
    @Transactional
    public Long adminOpenInquiry(Long roomId, Long adminId) {
        ChatRoom room = roomRepository.findById(roomId).filter(r -> r.getType() == ChatRoomType.INQUIRY)
                .orElseThrow(() -> new EntityNotFoundException("문의를 찾을 수 없어요."));
        Member admin = active(adminId);
        if (!admin.isAdmin()) throw new NoAccessException("관리자만 열 수 있어요.");
        syncService.join(room, admin);
        return room.getId();
    }

    private Long createOnce(Supplier<Long> create, Supplier<Optional<Long>> find) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(s -> create.get());
        } catch (DataIntegrityViolationException e) {
            entityManager.clear();
            Long id = tx.execute(s -> find.get().orElse(null));
            if (id == null) throw e;
            return tx.execute(s -> create.get());     // 먼저 만든 방에 내 참여만 맞춘다
        }
    }

    private Member active(Long id) {
        Member m = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
        if (!m.isActive()) throw new BusinessException(m.getDisplayName() + " 님은 퇴사했어요.");
        return m;
    }

    // ---------- 알림 끄기 · 나가기 · 문의 종료 ----------

    @Transactional
    public boolean setMuted(Long roomId, Long meId, boolean muted) {
        ChatParticipant p = myRow(roomId, meId);
        p.setMuted(muted);
        return p.isMuted();
    }

    @Transactional
    public void leave(Long roomId, Long meId) {
        ChatParticipant p = myRow(roomId, meId);
        ChatRoom r = p.getRoom();
        if (!r.getType().isLeavable()) throw new BusinessException(r.getType().getLabel() + "은 나갈 수 없어요.");
        if (r.getType() == ChatRoomType.INQUIRY && isHandler(r, p.getMember())
                && (r.getInquiryMember() == null || !r.getInquiryMember().getId().equals(meId))) {
            throw new BusinessException("문의 담당자는 문의 방을 나갈 수 없어요. 문의를 종료해 주세요.");
        }
        p.setLeftRoom(true);
        sessionRegistry.closeAfterCommit(p.getMember().getUsername());
    }

    @Transactional
    public void closeInquiry(Long roomId, Long meId) {
        ChatRoom r = roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new EntityNotFoundException("문의를 찾을 수 없어요."));
        Member me = memberRepository.findById(meId).orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
        if (r.getType() != ChatRoomType.INQUIRY) throw new BusinessException("문의 방이 아니에요.");
        if (!isHandler(r, me)) throw new NoAccessException("문의 담당자만 종료할 수 있어요.");
        r.setInquiryStatus(InquiryStatus.CLOSED);
    }
}
