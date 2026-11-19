package com.mysite.sbb.attendance.controller;

import com.mysite.sbb.attendance.service.AttendanceService;
import com.mysite.sbb.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// 관리자 : 날짜별 출퇴근 현황, 주간 스케줄 배정, 휴가 승인
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;
    private final MemberService memberService;

    @GetMapping("/attendance")
    public String attendance(@RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             Model model) {
        LocalDate d = date == null ? LocalDate.now() : date;
        model.addAttribute("date", d);
        model.addAttribute("records", attendanceService.getByDate(d));
        model.addAttribute("memberCount", memberService.getActiveMembers().size());
        return "admin/attendance";
    }

    // 배정 폼 값 유지 : memberId / date / startTime / endTime / memo 가 오면 폼에 그대로 채운다
    //  (한 직원의 근무를 며칠 이어서 배정할 때, 저장할 때마다 첫 직원(관리자)으로 돌아가지 않도록)
    @GetMapping("/schedule")
    public String schedule(@RequestParam(value = "week", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           ScheduleFormState form, Model model) {
        LocalDate monday = AttendanceService.weekStart(week);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(monday.plusDays(i));
        model.addAttribute("monday", monday);
        model.addAttribute("days", days);
        model.addAttribute("rows", attendanceService.getWeekTable(monday));
        model.addAttribute("members", memberService.getActiveMembers());
        model.addAttribute("form", form.withDefaultDate(monday));
        return "admin/schedule";
    }

    // 월별 스케줄 (who : 이 직원 근무만 보기, 비우면 전체)
    @GetMapping("/schedule/month")
    public String scheduleMonth(@RequestParam(value = "month", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                @RequestParam(value = "who", required = false) Long who,
                                ScheduleFormState form, Model model) {
        YearMonth m = month == null ? YearMonth.now() : month;
        model.addAttribute("cal", attendanceService.getMonthCalendar(m, who));
        model.addAttribute("who", who);
        model.addAttribute("members", memberService.getActiveMembers());
        model.addAttribute("form", form.withDefaultDate(m.equals(YearMonth.now()) ? LocalDate.now() : m.atDay(1)));
        return "admin/schedule-month";
    }

    @PostMapping("/schedule")
    public String addSchedule(@RequestParam("memberId") Long memberId,
                              @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                              @RequestParam("endTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                              @RequestParam(value = "memo", required = false) String memo,
                              @RequestParam(value = "view", required = false) String view,
                              @RequestParam(value = "who", required = false) Long who,
                              Principal principal, RedirectAttributes ra) {
        attendanceService.addSchedule(memberId, date, startTime, endTime, memo, principal.getName());
        ra.addFlashAttribute("adminMessage", memberService.getMember(memberId).getDisplayName() + " 님 "
                + date.getMonthValue() + "." + date.getDayOfMonth() + " 근무를 배정했어요. 다음 날짜로 넘어갔어요.");
        // 같은 직원·시간·메모를 유지하고, 날짜는 다음 날로 (이어서 배정하기 쉽게)
        LocalDate nextDate = date.plusDays(1);
        ra.addAttribute("memberId", memberId);
        ra.addAttribute("date", nextDate.toString());
        ra.addAttribute("startTime", startTime.toString());
        ra.addAttribute("endTime", endTime.toString());
        if (memo != null && !memo.isBlank()) ra.addAttribute("memo", memo);
        if ("month".equals(view)) {
            ra.addAttribute("month", YearMonth.from(date).toString());
            if (who != null) ra.addAttribute("who", who);
            return "redirect:/admin/schedule/month";
        }
        ra.addAttribute("week", AttendanceService.weekStart(date).toString());
        return "redirect:/admin/schedule";
    }

    @PostMapping("/schedule/{id}/delete")
    public String deleteSchedule(@PathVariable("id") Long id,
                                 @RequestParam(value = "week", required = false) String week,
                                 @RequestParam(value = "month", required = false) String month,
                                 @RequestParam(value = "who", required = false) Long who,
                                 RedirectAttributes ra) {
        attendanceService.deleteSchedule(id);
        if (month != null && !month.isBlank()) {
            ra.addAttribute("month", month);
            if (who != null) ra.addAttribute("who", who);
            return "redirect:/admin/schedule/month";
        }
        if (week != null) ra.addAttribute("week", week);
        return "redirect:/admin/schedule";
    }

    // 배정 폼에 다시 채울 값 (쿼리 파라미터 → 화면)
    @lombok.Getter
    @lombok.Setter
    public static class ScheduleFormState {
        private Long memberId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime startTime;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime endTime;
        private String memo;

        ScheduleFormState withDefaultDate(LocalDate fallback) {
            if (date == null) date = fallback;
            if (startTime == null) startTime = LocalTime.of(9, 0);
            if (endTime == null) endTime = LocalTime.of(18, 0);
            return this;
        }
    }

    @GetMapping("/leave")
    public String leave(Model model) {
        model.addAttribute("pending", attendanceService.getPendingLeaves());
        model.addAttribute("recent", attendanceService.getRecentLeaves());
        return "admin/leave";
    }

    @PostMapping("/leave/{id}")
    public String decideLeave(@PathVariable("id") Long id, @RequestParam("decision") String decision,
                              @RequestParam(value = "comment", required = false) String comment,
                              Principal principal, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        attendanceService.decideLeave(id, approve, comment, principal.getName());
        ra.addFlashAttribute("adminMessage", approve ? "휴가를 승인했어요." : "휴가를 반려했어요.");
        return "redirect:/admin/leave";
    }
}
