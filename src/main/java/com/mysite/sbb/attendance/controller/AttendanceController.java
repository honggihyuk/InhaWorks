package com.mysite.sbb.attendance.controller;

import com.mysite.sbb.attendance.constant.LeaveType;
import com.mysite.sbb.attendance.entity.WorkSchedule;
import com.mysite.sbb.attendance.service.AttendanceService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// 직원 : 출퇴근 / 스케줄(주간) / 휴가 신청
@Controller
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final MemberService memberService;

    @GetMapping("/attendance")
    public String attendance(Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        model.addAttribute("today", attendanceService.getToday(me));
        model.addAttribute("recent", attendanceService.getRecent(me, 31));
        model.addAttribute("now", LocalDate.now());
        return "attendance/index";
    }

    @PostMapping("/attendance/in")
    public String clockIn(Principal principal, RedirectAttributes ra) {
        try {
            attendanceService.clockIn(principal.getName());
            ra.addFlashAttribute("attendanceMessage", "출근했어요. 오늘도 힘내세요!");
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러 unique 제약에 걸린 경우 (먼저 들어간 요청이 이미 출근 처리)
            ra.addFlashAttribute("attendanceMessage", "이미 출근 처리됐어요.");
        }
        return "redirect:/attendance";
    }

    @PostMapping("/attendance/out")
    public String clockOut(Principal principal, RedirectAttributes ra) {
        attendanceService.clockOut(principal.getName());
        ra.addFlashAttribute("attendanceMessage", "퇴근했어요. 수고하셨어요!");
        return "redirect:/attendance";
    }

    @GetMapping("/schedule")
    public String schedule(@RequestParam(value = "week", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        LocalDate monday = AttendanceService.weekStart(week);
        List<WorkSchedule> mine = attendanceService.getMyWeek(me, monday);
        List<LocalDate> days = new ArrayList<>();
        List<List<WorkSchedule>> byDay = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            days.add(d);
            byDay.add(mine.stream().filter(s -> s.getWorkDate().equals(d)).toList());
        }
        model.addAttribute("monday", monday);
        model.addAttribute("days", days);
        model.addAttribute("byDay", byDay);
        model.addAttribute("today", LocalDate.now());
        return "attendance/schedule";
    }

    // 내 월별 스케줄
    @GetMapping("/schedule/month")
    public String scheduleMonth(@RequestParam(value = "month", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        model.addAttribute("cal", attendanceService.getMonthCalendar(month == null ? YearMonth.now() : month, me.getId()));
        return "attendance/schedule-month";
    }

    @GetMapping("/leave")
    public String leave(Principal principal, Model model) {
        model.addAttribute("leaves", attendanceService.getMyLeaves(memberService.getMember(principal.getName())));
        model.addAttribute("types", LeaveType.values());
        model.addAttribute("today", LocalDate.now());
        return "attendance/leave";
    }

    @PostMapping("/leave")
    public String requestLeave(@RequestParam("type") LeaveType type,
                               @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                               @RequestParam(value = "reason", required = false) String reason,
                               Principal principal, RedirectAttributes ra) {
        attendanceService.requestLeave(principal.getName(), type, startDate, endDate, reason);
        ra.addFlashAttribute("leaveMessage", "휴가를 신청했어요. 관리자 승인을 기다려 주세요.");
        return "redirect:/leave";
    }

    @PostMapping("/leave/{id}/cancel")
    public String cancelLeave(@PathVariable("id") Long id, Principal principal) {
        attendanceService.cancelLeave(id, principal.getName());
        return "redirect:/leave";
    }
}
