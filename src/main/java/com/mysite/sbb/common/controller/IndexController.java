package com.mysite.sbb.common.controller;

import com.mysite.sbb.attendance.service.AttendanceService;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.service.NotificationService;
import com.mysite.sbb.report.service.ReportService;
import com.mysite.sbb.todo.service.TodoAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.LocalDate;

// 메인 화면 : 오늘 출퇴근, 오늘 할 일, 결재 대기, 새 알림, 고정 공지·최근 글
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final MemberService memberService;
    private final AttendanceService attendanceService;
    private final TodoAssignmentService todoAssignmentService;
    private final ReportService reportService;
    private final BoardService boardService;
    private final NotificationService notificationService;

    @GetMapping("/")
    public String index(Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        if (me.isAdmin()) return "redirect:/admin";      // 관리자의 첫 화면은 관리자 대시보드
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        model.addAttribute("attendance", attendanceService.getToday(me));
        model.addAttribute("todos", todoAssignmentService.getMine(me, today));
        model.addAttribute("todoLeft", todoAssignmentService.countMyIncomplete(me, today));
        model.addAttribute("reviewWaiting", todoAssignmentService.countReviewWaiting(me));
        model.addAttribute("approvalWaiting", reportService.getInbox(me).size());
        model.addAttribute("unreadTop", notificationService.getUnreadTop(me));
        model.addAttribute("pinned", boardService.getPinned(me));
        model.addAttribute("recentPosts", boardService.getRecent(me, 5));
        return "index";
    }

    // 권한이 없는 화면
    @GetMapping("/denied")
    public String denied() {
        return "denied";
    }
}
