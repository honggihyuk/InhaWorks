package com.mysite.sbb.chat.controller;

import com.mysite.sbb.chat.service.ChatRoomService;
import com.mysite.sbb.chat.service.InquiryTopicService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

// 채팅 화면 : 왼쪽 대화방 목록 + 오른쪽 대화창 (모바일은 한 단씩)
@Controller
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatRoomService roomService;
    private final InquiryTopicService topicService;
    private final MemberService memberService;

    @GetMapping
    public String index(@RequestParam(value = "q", required = false) String q, Principal principal, Model model) {
        return page(null, q, me(principal), model);
    }

    @GetMapping("/rooms/{id}")
    public String room(@PathVariable("id") Long id, Principal principal, Model model) {
        return page(id, null, me(principal), model);
    }

    // 목록만 다시 그리기 (새 메시지·검색) : 서버가 그린 조각을 그대로 끼운다
    @GetMapping("/rooms/list")
    public String list(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "active", required = false) Long active, Principal principal, Model model) {
        model.addAttribute("rooms", roomService.rooms(me(principal).getId(), q));
        model.addAttribute("activeId", active);
        return "chat/fragments :: roomList";
    }

    private String page(Long roomId, String q, Member me, Model model) {
        model.addAttribute("rooms", roomService.rooms(me.getId(), q));
        model.addAttribute("q", q);
        model.addAttribute("activeId", roomId);
        if (roomId != null) model.addAttribute("room", roomService.view(roomId, me.getId()));
        model.addAttribute("people", memberService.getActiveMembersWithOrg().stream().filter(m -> !m.getId().equals(me.getId())).toList());
        model.addAttribute("topics", topicService.available());
        return "chat/index";
    }

    @PostMapping("/direct/{memberId}")
    public String direct(@PathVariable("memberId") Long memberId, Principal principal) {
        return "redirect:/chat/rooms/" + roomService.openDirect(me(principal).getId(), memberId);
    }

    @PostMapping("/group")
    public String group(@RequestParam(value = "name", required = false) String name,
                        @RequestParam(value = "memberIds", required = false) List<Long> memberIds,
                        Principal principal, RedirectAttributes ra) {
        try {
            return "redirect:/chat/rooms/" + roomService.createGroup(me(principal).getId(), name, memberIds);
        } catch (com.mysite.sbb.common.exception.BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/chat";
        }
    }

    @GetMapping("/inquiry")
    public String inquiry(Model model) {
        model.addAttribute("topics", topicService.available());
        return "chat/inquiry";
    }

    @PostMapping("/inquiry/{topicId}")
    public String openInquiry(@PathVariable("topicId") Long topicId, Principal principal) {
        return "redirect:/chat/rooms/" + roomService.openInquiry(me(principal).getId(), topicId);
    }

    @PostMapping("/rooms/{id}/inquiry/close")
    public String closeInquiry(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        roomService.closeInquiry(id, me(principal).getId());
        ra.addFlashAttribute("attendanceMessage", "문의를 종료했어요.");
        return "redirect:/chat/rooms/" + id;
    }

    private Member me(Principal principal) {
        return memberService.getMember(principal.getName());
    }
}
