package com.mysite.sbb.chat.service;

import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.entity.ChatParticipant;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.chat.repository.ChatRoomRepository;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.entity.Workplace;
import com.mysite.sbb.organization.repository.TeamRepository;
import com.mysite.sbb.organization.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

// 매장·팀·공지 채널과 소속 직원을 맞춘다 (CHT-03, 04, 08). 몇 번을 돌려도 결과가 같다 (멱등)
//  - 시작할 때 전체 동기화 (기존 데이터)
//  - 근무지·팀 생성, 직원 등록·소속 변경·퇴사·복직 때 그 부분만
@Service
@RequiredArgsConstructor
@Transactional
public class ChatSyncService {

    public static final String NOTICE_KEY = "NOTICE";

    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;
    private final WorkplaceRepository workplaceRepository;
    private final ChatSessionRegistry sessionRegistry;

    public void syncAll() {
        noticeRoom();
        workplaceRepository.findAll().forEach(this::workplaceRoom);
        teamRepository.findAll().forEach(this::teamRoom);
        memberRepository.findAll().forEach(this::syncMember);
    }

    public ChatRoom noticeRoom() {
        return roomRepository.findByChannelKey(NOTICE_KEY).orElseGet(() -> roomRepository.save(ChatRoom.builder()
                .type(ChatRoomType.NOTICE).name("전체 공지").channelKey(NOTICE_KEY).created(LocalDateTime.now()).build()));
    }

    public ChatRoom workplaceRoom(Workplace w) {
        ChatRoom room = roomRepository.findByChannelKey("W:" + w.getId()).orElseGet(() -> roomRepository.save(ChatRoom.builder()
                .type(ChatRoomType.WORKPLACE).name(w.getName()).workplace(w).channelKey("W:" + w.getId())
                .created(LocalDateTime.now()).build()));
        if (!Objects.equals(room.getName(), w.getName())) room.setName(w.getName());
        return room;
    }

    public ChatRoom teamRoom(Team t) {
        ChatRoom room = roomRepository.findByChannelKey("T:" + t.getId()).orElseGet(() -> roomRepository.save(ChatRoom.builder()
                .type(ChatRoomType.TEAM).name(t.getName()).team(t).channelKey("T:" + t.getId())
                .created(LocalDateTime.now()).build()));
        if (!Objects.equals(room.getName(), t.getName())) room.setName(t.getName());
        return room;
    }

    // 직원 한 명의 참여 상태 맞추기 : 재직자는 공지 + 내 매장 + 내 팀, 퇴사자는 모든 방에서 빠진다
    public void syncMember(Member m) {
        List<ChatParticipant> mine = participantRepository.findByMemberId(m.getId());
        boolean removed = false;
        if (!m.isActive()) {
            for (ChatParticipant p : mine) {
                if (!p.isLeftRoom()) {
                    p.setLeftRoom(true);
                    removed = true;
                }
            }
        } else {
            Set<Long> wanted = new HashSet<>();
            ChatRoom notice = noticeRoom();
            wanted.add(notice.getId());
            join(notice, m);
            if (m.getWorkplace() != null) {
                ChatRoom r = workplaceRoom(m.getWorkplace());
                wanted.add(r.getId());
                join(r, m);
            }
            if (m.getTeam() != null) {
                ChatRoom r = teamRoom(m.getTeam());
                wanted.add(r.getId());
                join(r, m);
            }
            for (ChatParticipant p : mine) {
                ChatRoomType type = p.getRoom().getType();
                boolean channel = type == ChatRoomType.WORKPLACE || type == ChatRoomType.TEAM;
                if (channel && !p.isLeftRoom() && !wanted.contains(p.getRoom().getId())) {
                    p.setLeftRoom(true);
                    removed = true;
                }
            }
        }
        if (removed) sessionRegistry.closeAfterCommit(m.getUsername());
    }

    // 참여 (이미 있으면 되돌리기만). 새로 들어온 사람은 지난 메시지를 모두 읽은 것으로 시작한다
    public ChatParticipant join(ChatRoom room, Member m) {
        Optional<ChatParticipant> found = participantRepository.findByRoomIdAndMemberId(room.getId(), m.getId());
        long last = room.getLastMessageId() == null ? 0 : room.getLastMessageId();
        if (found.isPresent()) {
            ChatParticipant p = found.get();
            if (p.isLeftRoom()) {
                p.setLeftRoom(false);
                p.setLastReadMessageId(Math.max(p.getLastReadMessageId(), last));
                p.setJoinedAt(LocalDateTime.now());
            }
            return p;
        }
        return participantRepository.save(ChatParticipant.builder().room(room).member(m)
                .lastReadMessageId(last).joinedAt(LocalDateTime.now()).build());
    }

    // 팀·근무지를 지우기 전에 채널을 보관 처리 (외래 키 해제, 대화 기록은 남긴다)
    public void archiveTeamRoom(Long teamId) {
        roomRepository.findByChannelKey("T:" + teamId).ifPresent(r -> {
            r.setTeam(null);
            archive(r);
        });
    }

    public void archiveWorkplaceRoom(Long workplaceId) {
        roomRepository.findByChannelKey("W:" + workplaceId).ifPresent(r -> {
            r.setWorkplace(null);
            archive(r);
        });
    }

    private void archive(ChatRoom r) {
        r.setChannelKey(null);
        r.setArchived(true);
        for (ChatParticipant p : participantRepository.findByRoomId(r.getId())) {
            if (!p.isLeftRoom()) {
                p.setLeftRoom(true);
                sessionRegistry.closeAfterCommit(p.getMember().getUsername());
            }
        }
    }
}
