package com.mysite.sbb.chat;

import com.mysite.sbb.admin.dto.AdminMemberDto;
import com.mysite.sbb.admin.service.AdminMemberService;
import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.constant.InquiryStatus;
import com.mysite.sbb.chat.dto.ChatMessageDto;
import com.mysite.sbb.chat.dto.ChatMessageForm;
import com.mysite.sbb.chat.dto.InquiryTopicForm;
import com.mysite.sbb.chat.dto.MemberCard;
import com.mysite.sbb.chat.entity.ChatParticipant;
import com.mysite.sbb.chat.entity.ChatRoom;
import com.mysite.sbb.chat.entity.InquiryTopic;
import com.mysite.sbb.chat.repository.ChatMessageRepository;
import com.mysite.sbb.chat.repository.ChatParticipantRepository;
import com.mysite.sbb.chat.repository.ChatRoomRepository;
import com.mysite.sbb.chat.service.*;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.notification.repository.NotificationRepository;
import com.mysite.sbb.organization.service.OrganizationService;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import com.mysite.sbb.outbox.service.OutboxProcessor;
import com.mysite.sbb.outbox.service.OutboxRelay;
import com.mysite.sbb.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 사내 채팅 : 채널 동기화, 1:1 유일, 멱등 전송, 읽음, 멘션·알림, 나가기, 문의, 퇴사, 재처리, 권한
@SpringBootTest(properties = {"app.upload-dir=build/test-uploads-chat", "app.private-dir=build/test-private-chat"})
class ChatFlowTest {

    @Autowired ChatRoomService roomService;
    @Autowired ChatMessageService messageService;
    @Autowired ChatSyncService syncService;
    @Autowired InquiryTopicService topicService;
    @Autowired ChatAdminService adminService;
    @Autowired MemberCardService cardService;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired ChatParticipantRepository participantRepository;
    @Autowired ChatMessageRepository messageRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired OutboxProcessor outboxProcessor;
    @Autowired OutboxRelay outboxRelay;
    @Autowired OutboxService outboxService;
    @Autowired OrganizationService organizationService;
    @Autowired AdminMemberService adminMemberService;
    @Autowired TransactionTemplate tx;
    @Autowired WebApplicationContext context;
    @MockitoSpyBean SimpMessagingTemplate messagingTemplate;

    Member admin, emp1, emp2, emp3;

    @BeforeEach
    void setUp() {
        admin = member("admin");
        emp1 = member("emp1");
        emp2 = member("emp2");
        emp3 = member("emp3");
        outboxRelay.pollOnce();
    }

    @Test
    @DisplayName("CHT-02~04 : 공지·매장·팀 채널이 만들어지고 소속 직원이 자동 참여한다, 새 근무지도 채널 생성")
    void channelsSynced() {
        Long notice = syncService.noticeRoom().getId();
        assertThat(participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(notice, emp3.getId())).isTrue();
        List<String> types = roomService.rooms(emp2.getId(), null).stream().map(r -> r.type()).toList();
        assertThat(types).contains("NOTICE", "WORKPLACE", "TEAM");
        long rooms = roomRepository.count();
        syncService.syncAll();
        assertThat(roomRepository.count()).isEqualTo(rooms);    // 멱등

        String name = "새매장-" + UUID.randomUUID().toString().substring(0, 6);
        organizationService.createWorkplace(name, null);
        assertThat(roomRepository.findByType(ChatRoomType.WORKPLACE)).anyMatch(r -> name.equals(r.getName()));
    }

    @Test
    @DisplayName("CHT-03 : 직원의 소속이 바뀌면 참여 채널도 바뀐다")
    void membershipFollowsOrg() {
        Member m = createMember();
        Long teamRoom = tx.execute(s -> syncService.teamRoom(memberRepository.findById(emp1.getId()).orElseThrow().getTeam()).getId());
        Long otherTeam = tx.execute(s -> memberRepository.findById(emp2.getId()).orElseThrow().getTeam().getId());
        Long myTeam = tx.execute(s -> memberRepository.findById(emp1.getId()).orElseThrow().getTeam().getId());
        assertThat(participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(teamRoom, m.getId())).isTrue();

        AdminMemberDto dto = tx.execute(s -> AdminMemberDto.of(memberRepository.findById(m.getId()).orElseThrow()));
        dto.setTeamId(otherTeam);
        adminMemberService.update(m.getId(), dto);

        assertThat(myTeam).isNotEqualTo(otherTeam);
        assertThat(participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(teamRoom, m.getId())).isFalse();
    }

    @Test
    @DisplayName("AC-11 : 같은 두 사람의 1:1 방은 하나")
    void directIsUnique() {
        Long a = roomService.openDirect(emp1.getId(), emp2.getId());
        Long b = roomService.openDirect(emp2.getId(), emp1.getId());
        assertThat(a).isEqualTo(b);
        assertThatThrownBy(() -> roomService.openDirect(emp1.getId(), emp1.getId())).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("AC-07 · REL-20 : 같은 clientMessageId 는 한 번만 저장되고 기존 메시지를 돌려준다")
    void sendIsIdempotent() {
        Long room = roomService.openDirect(emp1.getId(), emp3.getId());
        long messages = messageRepository.count(), events = outboxRepository.count();
        ChatMessageForm form = form("안녕하세요");
        ChatMessageDto first = messageService.send(room, emp1.getId(), form, null);
        ChatMessageDto second = messageService.send(room, emp1.getId(), form, null);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(messageRepository.count()).isEqualTo(messages + 1);
        assertThat(outboxRepository.count()).isEqualTo(events + 1);
    }

    @Test
    @DisplayName("SEC-08 : 본문은 그대로 저장되고 화면 DTO 는 HTML 로 바꾸지 않는다, 2000자·형식·개수 제한")
    void contentAndFileLimits() {
        Long room = roomService.openDirect(emp2.getId(), emp3.getId());
        ChatMessageDto dto = messageService.send(room, emp2.getId(), form("<script>alert(1)</script>"), null);
        assertThat(dto.content()).isEqualTo("<script>alert(1)</script>");
        assertThatThrownBy(() -> messageService.send(room, emp2.getId(), form("x"),
                List.of(new MockMultipartFile("files", "evil.exe", "application/octet-stream", new byte[]{1}))))
                .isInstanceOf(IllegalArgumentException.class);
        List<org.springframework.web.multipart.MultipartFile> many = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) many.add(new MockMultipartFile("files", i + ".png", "image/png", new byte[]{1}));
        assertThatThrownBy(() -> messageService.send(room, emp2.getId(), form("x"), many)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> messageService.send(room, emp2.getId(), form("  "), null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CHT-04 : 공지 채널은 관리자만 쓴다")
    void noticeAdminOnly() {
        Long notice = syncService.noticeRoom().getId();
        assertThatThrownBy(() -> messageService.send(notice, emp2.getId(), form("직원 공지"), null)).isInstanceOf(NoAccessException.class);
        assertThat(messageService.send(notice, admin.getId(), form("관리자 공지"), null).id()).isNotNull();
    }

    @Test
    @DisplayName("AC-08 · REL-22 : afterId 로 빠진 메시지를 순서대로 받고, 읽음 위치는 뒤로 가지 않는다")
    void catchUpAndRead() {
        Long room = roomService.openDirect(emp1.getId(), admin.getId());
        ChatMessageDto m1 = messageService.send(room, emp1.getId(), form("1"), null);
        ChatMessageDto m2 = messageService.send(room, emp1.getId(), form("2"), null);
        ChatMessageDto m3 = messageService.send(room, emp1.getId(), form("3"), null);
        assertThat(messageService.messages(room, admin.getId(), null, m1.id())).extracting(ChatMessageDto::id).containsExactly(m2.id(), m3.id());

        messageService.markRead(room, admin.getId(), m3.id());
        messageService.markRead(room, admin.getId(), m1.id());      // 늦게 도착한 예전 요청
        assertThat(participantRepository.findByRoomIdAndMemberId(room, admin.getId()).orElseThrow().getLastReadMessageId()).isEqualTo(m3.id());
        // 방에 없는 큰 값은 마지막 메시지로 잘린다
        messageService.markRead(room, admin.getId(), Long.MAX_VALUE);
        assertThat(participantRepository.findByRoomIdAndMemberId(room, admin.getId()).orElseThrow().getLastReadMessageId()).isEqualTo(m3.id());
    }

    @Test
    @DisplayName("CHT-26 : 이전 50개씩")
    void pagingBefore() {
        Long room = roomService.createGroup(emp1.getId(), "페이징", List.of(emp2.getId(), emp3.getId()));
        ChatMessageDto last = null;
        for (int i = 0; i < 55; i++) last = messageService.send(room, emp1.getId(), form("m" + i), null);
        List<ChatMessageDto> latest = messageService.messages(room, emp2.getId(), null, null);
        assertThat(latest).hasSize(50);
        assertThat(latest.get(49).id()).isEqualTo(last.id());
        assertThat(messageService.messages(room, emp2.getId(), latest.get(0).id(), null)).hasSize(5);
    }

    @Test
    @DisplayName("AC-09 : 알림 끈 방의 일반 메시지는 🔔 없음, 멘션은 🔔 (재처리해도 한 번)")
    void muteAndMention() {
        Long room = roomService.createGroup(emp1.getId(), "멘션방", List.of(emp2.getId(), emp3.getId()));
        roomService.setMuted(room, emp2.getId(), true);
        long before = notificationRepository.countByRecipientIdAndReadFalse(emp2.getId());

        messageService.send(room, emp1.getId(), form("일반 메시지"), null);
        outboxRelay.pollOnce();
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(emp2.getId())).isEqualTo(before);

        ChatMessageForm mention = form("@emp2 확인 부탁해요");
        ChatMessageDto dto = messageService.send(room, emp1.getId(), mention, null);
        assertThat(dto.mentionIds()).containsExactly(emp2.getId());
        outboxRelay.pollOnce();
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(emp2.getId())).isEqualTo(before + 1);
        assertThat(notificationRepository.findTop50ByRecipientIdOrderByIdDesc(emp2.getId()).get(0).getType()).isEqualTo(NotificationType.MENTION);

        OutboxEvent event = latestChatEvent();
        outboxService.reprocess(event.getId());
        outboxRelay.pollOnce();
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(emp2.getId())).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("AC-13 : 브로드캐스트 실패 → 재시도 → DEAD → 재처리 → DONE, 메시지는 하나")
    void broadcastFailureGoesDeadAndRecovers() {
        Long room = roomService.openDirect(emp2.getId(), admin.getId());
        ChatMessageDto dto = messageService.send(room, emp2.getId(), form("재처리"), null);
        OutboxEvent event = latestChatEvent();
        doThrow(new IllegalStateException("브로커 장애 (주입)")).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        try {
            for (int i = 0; i < 3; i++) outboxProcessor.process(event.getId());
        } finally {
            reset(messagingTemplate);
        }
        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(messageRepository.findById(dto.id())).isPresent();

        outboxService.reprocess(event.getId());
        assertThat(outboxProcessor.process(event.getId())).isTrue();
        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    @DisplayName("CHT-36 : 매장·팀·공지는 나갈 수 없고, 나간 방은 새 메시지가 와도 다시 들어오지 않는다")
    void leave() {
        Long notice = syncService.noticeRoom().getId();
        assertThatThrownBy(() -> roomService.leave(notice, emp2.getId())).isInstanceOf(BusinessException.class);

        Long group = roomService.createGroup(emp1.getId(), "나가기", List.of(emp2.getId(), emp3.getId()));
        roomService.leave(group, emp3.getId());
        messageService.send(group, emp1.getId(), form("나간 뒤 메시지"), null);
        outboxRelay.pollOnce();
        assertThat(participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(group, emp3.getId())).isFalse();
        assertThatThrownBy(() -> messageService.messages(group, emp3.getId(), null, null)).isInstanceOf(NoAccessException.class);
    }

    @Test
    @DisplayName("AC-10 · CHT-41~43 : 문의 방은 직원+담당자만, 상태 미답변 → 답변 중 → 종료 → 미답변")
    void inquiryFlow() {
        InquiryTopic topic = topic("인사 문의-" + UUID.randomUUID().toString().substring(0, 6), emp1.getId());
        assertThat(topicService.available()).extracting(InquiryTopic::getId).contains(topic.getId());
        Long room = roomService.openInquiry(emp2.getId(), topic.getId());
        assertThat(roomService.openInquiry(emp2.getId(), topic.getId())).isEqualTo(room);
        assertThat(inquiry(room)).isEqualTo(InquiryStatus.OPEN);
        assertThatThrownBy(() -> messageService.messages(room, emp3.getId(), null, null)).isInstanceOf(NoAccessException.class);

        messageService.send(room, emp2.getId(), form("급여 문의"), null);
        assertThat(inquiry(room)).isEqualTo(InquiryStatus.OPEN);
        messageService.send(room, emp1.getId(), form("확인할게요"), null);
        assertThat(inquiry(room)).isEqualTo(InquiryStatus.ANSWERING);
        assertThatThrownBy(() -> roomService.closeInquiry(room, emp2.getId())).isInstanceOf(NoAccessException.class);
        roomService.closeInquiry(room, emp1.getId());
        assertThat(inquiry(room)).isEqualTo(InquiryStatus.CLOSED);
        messageService.send(room, emp2.getId(), form("하나 더요"), null);
        assertThat(inquiry(room)).isEqualTo(InquiryStatus.OPEN);
        assertThat(adminService.inquiries(topic.getId(), InquiryStatus.OPEN)).hasSize(1);
    }

    @Test
    @DisplayName("문의 담당자는 재직 중인 관리자·매니저만")
    void topicHandlerGuard() {
        InquiryTopicForm form = new InquiryTopicForm();
        form.setName("잘못된 담당자");
        form.setHandlerIds(List.of(emp2.getId()));
        assertThatThrownBy(() -> topicService.save(form)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CHT-08 : 퇴사자는 모든 방에서 빠지고, 지난 메시지에 (퇴사) 표시, 복직하면 소속 채널로")
    void resignation() {
        Member m = createMember();
        Long direct = roomService.openDirect(m.getId(), emp1.getId());
        messageService.send(direct, m.getId(), form("마지막 인사"), null);

        adminMemberService.toggleActive(m.getId());
        assertThat(participantRepository.findByMemberId(m.getId())).allMatch(ChatParticipant::isLeftRoom);
        assertThat(messageService.messages(direct, emp1.getId(), null, null).get(0).sender().resigned()).isTrue();
        assertThatThrownBy(() -> messageService.send(direct, emp1.getId(), form("받을 사람 없음"), null)).isInstanceOf(BusinessException.class);

        adminMemberService.toggleActive(m.getId());
        assertThat(participantRepository.existsByRoomIdAndMemberIdAndLeftRoomFalse(syncService.noticeRoom().getId(), m.getId())).isTrue();
    }

    @Test
    @DisplayName("CHT-11 · SCH-01 : 프로필 카드 근무 상태·근무제, 화면에 칩이 나온다")
    void memberCard() throws Exception {
        MemberCard card = cardService.card(emp2.getId(), emp1.getId());
        assertThat(card.workStatus()).isIn("출근 전", "근무 중", "퇴근", "휴가");
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/members/" + emp2.getId() + "/card").with(user("emp1").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("policy-chip")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("채팅하기")));
    }

    @Test
    @DisplayName("화면·API : 목록과 대화방이 그려지고, 다른 사람의 1:1 은 403, 관리자도 1:1 본문 불가, CSRF 없는 전송 거부")
    void screensAndPermissions() throws Exception {
        Long direct = roomService.openDirect(emp1.getId(), emp2.getId());
        ChatMessageDto dto = messageService.send(direct, emp1.getId(), form("비밀 대화 본문"), List.of(
                new MockMultipartFile("files", "사진.png", "image/png", new byte[]{1, 2, 3})));
        Long attachment = dto.attachments().get(0).id();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(get("/chat").with(user("emp1").roles("MANAGER"))).andExpect(status().isOk());
        mvc.perform(get("/chat/rooms/" + direct).with(user("emp1").roles("MANAGER"))).andExpect(status().isOk());
        mvc.perform(get("/chat/inquiry").with(user("emp2").roles("USER"))).andExpect(status().isOk());
        mvc.perform(get("/chat/rooms/" + direct).with(user("emp3").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/chat/rooms/" + direct + "/messages").with(user("emp3").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/chat/rooms/" + direct + "/messages").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/chat/attachments/" + attachment).with(user("emp3").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/chat/attachments/" + attachment).with(user("emp2").roles("USER"))).andExpect(status().isOk());
        mvc.perform(multipart("/chat/rooms/" + direct + "/messages").param("clientMessageId", "no-csrf").param("content", "x")
                .with(user("emp1").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/chat/rooms/" + direct + "/messages").param("clientMessageId", UUID.randomUUID().toString())
                .param("content", "x".repeat(2001)).with(csrf()).with(user("emp1").roles("MANAGER"))).andExpect(status().isBadRequest());

        String page = mvc.perform(get("/admin/chat").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("비밀 대화 본문");
        mvc.perform(get("/admin/chat/topics").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/chat/inquiries").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/chat").with(user("emp1").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/monitor").param("type", OutboxEventType()).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    private static String OutboxEventType() {
        return ChatMessageOutboxHandler.TYPE;
    }

    private InquiryStatus inquiry(Long room) {
        return roomRepository.findById(room).map(ChatRoom::getInquiryStatus).orElseThrow();
    }

    private InquiryTopic topic(String name, Long handlerId) {
        InquiryTopicForm f = new InquiryTopicForm();
        f.setName(name);
        f.setHandlerIds(List.of(handlerId));
        return topicService.save(f);
    }

    private OutboxEvent latestChatEvent() {
        return outboxService.getEvents(null, ChatMessageOutboxHandler.TYPE).get(0);
    }

    private Member createMember() {
        AdminMemberDto dto = new AdminMemberDto();
        dto.setUsername("chat" + UUID.randomUUID().toString().substring(0, 8));
        dto.setName("테스트직원");
        dto.setPassword("1234");
        dto.setRole(MemberRole.USER);
        dto.setActive(true);
        Long[] org = tx.execute(s -> {
            Member e = memberRepository.findById(emp1.getId()).orElseThrow();
            return new Long[]{e.getTeam().getId(), e.getWorkplace().getId()};
        });
        dto.setTeamId(org[0]);
        dto.setWorkplaceId(org[1]);
        return adminMemberService.create(dto);
    }

    private Member member(String username) {
        return memberRepository.findByUsername(username).orElseThrow();
    }

    private static ChatMessageForm form(String content) {
        ChatMessageForm f = new ChatMessageForm();
        f.setClientMessageId(UUID.randomUUID().toString());
        f.setContent(content);
        return f;
    }
}
