package com.mysite.sbb.chat.controller;

import com.mysite.sbb.chat.constant.InquiryStatus;
import com.mysite.sbb.chat.dto.ChatMessageForm;
import com.mysite.sbb.chat.dto.InquiryTopicForm;
import com.mysite.sbb.chat.entity.InquiryTopic;
import com.mysite.sbb.chat.service.*;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

// 관리자 채팅 : 채널 현황·공지 작성, 문의 주제, 문의함. 1:1 본문을 보는 화면은 두지 않는다 (CHT-52)
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/chat")
public class AdminChatController {

    private final ChatAdminService adminService;
    private final ChatRoomService roomService;
    private final ChatMessageService messageService;
    private final ChatSyncService syncService;
    private final InquiryTopicService topicService;
    private final MemberService memberService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("channels", adminService.channels());
        model.addAttribute("directRooms", adminService.directRooms());
        model.addAttribute("directMessages", adminService.directMessages());
        model.addAttribute("openInquiries", adminService.countInquiries(InquiryStatus.OPEN));
        model.addAttribute("noticeToken", UUID.randomUUID().toString());
        return "admin/chat";
    }

    @PostMapping("/notice")
    public String notice(@RequestParam("content") String content, @RequestParam("clientMessageId") String token,
                         Principal principal, RedirectAttributes ra) {
        Member me = memberService.getMember(principal.getName());
        ChatMessageForm form = new ChatMessageForm();
        form.setClientMessageId(token);
        form.setContent(content);
        try {
            if (content == null || content.isBlank()) throw new BusinessException("공지 내용을 입력해 주세요.");
            if (content.length() > 2000) throw new BusinessException("메시지는 2,000자까지 보낼 수 있어요.");
            messageService.send(syncService.noticeRoom().getId(), me.getId(), form, null);
            ra.addFlashAttribute("adminMessage", "전체 공지를 보냈어요.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/chat";
    }

    @GetMapping("/topics")
    public String topics(@RequestParam(value = "edit", required = false) Long edit, Model model) {
        InquiryTopicForm form = new InquiryTopicForm();
        if (edit != null) {
            topicService.all().stream().filter(t -> t.getId().equals(edit)).findFirst().ifPresent(t -> fill(form, t));
        }
        model.addAttribute("inquiryTopicForm", form);
        return topicsView(model);
    }

    @PostMapping("/topics")
    public String saveTopic(@Valid @ModelAttribute("inquiryTopicForm") InquiryTopicForm form, BindingResult binding,
                            Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) return topicsView(model);
        try {
            topicService.save(form);
        } catch (BusinessException e) {
            binding.reject("topic", e.getMessage());
            return topicsView(model);
        }
        ra.addFlashAttribute("adminMessage", "문의 주제를 저장했어요.");
        return "redirect:/admin/chat/topics";
    }

    @PostMapping("/topics/{id}/toggle")
    public String toggleTopic(@PathVariable("id") Long id, RedirectAttributes ra) {
        topicService.toggle(id);
        ra.addFlashAttribute("adminMessage", "사용 여부를 바꿨어요.");
        return "redirect:/admin/chat/topics";
    }

    private String topicsView(Model model) {
        model.addAttribute("topics", topicService.all());
        model.addAttribute("handlers", memberService.getActiveMembers().stream()
                .filter(m -> m.getRole() == MemberRole.ADMIN || m.getRole() == MemberRole.MANAGER).toList());
        return "admin/chat-topics";
    }

    private static void fill(InquiryTopicForm form, InquiryTopic t) {
        form.setId(t.getId());
        form.setName(t.getName());
        form.setDescription(t.getDescription());
        form.setIcon(t.getIcon());
        form.setActive(t.isActive());
        form.setHandlerIds(t.getHandlers().stream().map(Member::getId).toList());
    }

    @GetMapping("/inquiries")
    public String inquiries(@RequestParam(value = "topic", required = false) Long topic,
                            @RequestParam(value = "status", required = false) InquiryStatus status, Model model) {
        model.addAttribute("topics", topicService.all());
        model.addAttribute("topic", topic);
        model.addAttribute("status", status);
        model.addAttribute("statuses", InquiryStatus.values());
        model.addAttribute("inquiries", adminService.inquiries(topic, status));
        return "admin/chat-inquiries";
    }

    @PostMapping("/inquiries/{id}/open")
    public String openInquiry(@PathVariable("id") Long id, Principal principal) {
        return "redirect:/chat/rooms/" + roomService.adminOpenInquiry(id, memberService.getMember(principal.getName()).getId());
    }

    @PostMapping("/inquiries/{id}/close")
    public String closeInquiry(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        roomService.closeInquiry(id, memberService.getMember(principal.getName()).getId());
        ra.addFlashAttribute("adminMessage", "문의를 종료했어요.");
        return "redirect:/admin/chat/inquiries";
    }
}
