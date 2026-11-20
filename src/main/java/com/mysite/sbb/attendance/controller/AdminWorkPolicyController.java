package com.mysite.sbb.attendance.controller;

import com.mysite.sbb.attendance.constant.Tone;
import com.mysite.sbb.attendance.constant.WorkPolicyType;
import com.mysite.sbb.attendance.dto.WorkPolicyForm;
import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.attendance.service.WorkPolicyService;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

// 관리자 : 근무 정책(근무제) 관리 — 정책 만들기·고치기·지우기, 근무 유형, 소속 직원 옮기기
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/schedule/policies")
public class AdminWorkPolicyController {

    private final WorkPolicyService workPolicyService;
    private final MemberService memberService;
    private final OrganizationService organizationService;

    private void common(Model model) {
        model.addAttribute("types", WorkPolicyType.values());
        model.addAttribute("tones", Tone.values());
        model.addAttribute("weekDays", WorkPolicy.WEEK);
        model.addAttribute("workplaces", organizationService.getWorkplaces());
        model.addAttribute("tabs", workPolicyService.getTabs());
    }

    // 목록 + 새 정책 폼
    @GetMapping
    public String list(Model model) {
        common(model);
        model.addAttribute("policyForm", new WorkPolicyForm());
        return "admin/policy-list";
    }

    @PostMapping
    public String create(WorkPolicyForm policyForm, RedirectAttributes ra) {
        WorkPolicy p = workPolicyService.create(policyForm);
        ra.addFlashAttribute("adminMessage", "‘" + p.getName() + "’ 근무 정책을 만들었어요. 근무 유형과 직원을 정해 주세요.");
        return "redirect:/admin/schedule/policies/" + p.getId();
    }

    // 상세 : 정책 수정 + 근무 유형 + 소속 직원
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        WorkPolicy p = workPolicyService.getPolicy(id);
        common(model);
        model.addAttribute("policy", p);
        model.addAttribute("policyForm", WorkPolicyForm.of(p));
        model.addAttribute("members", memberService.getActiveMembers());
        return "admin/policy-detail";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id, WorkPolicyForm policyForm, RedirectAttributes ra) {
        workPolicyService.update(id, policyForm);
        ra.addFlashAttribute("adminMessage", "근무 정책을 고쳤어요.");
        return "redirect:/admin/schedule/policies/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            String name = workPolicyService.delete(id);
            ra.addFlashAttribute("adminMessage", "‘" + name + "’ 근무 정책을 지웠어요.");
            return "redirect:/admin/schedule/policies";
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/schedule/policies/" + id;
        }
    }

    @PostMapping("/{id}/shifts")
    public String addShift(@PathVariable("id") Long id,
                           @RequestParam("name") String name,
                           @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
                           @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end,
                           @RequestParam(value = "off", defaultValue = "false") boolean off,
                           @RequestParam(value = "tone", required = false) Tone tone,
                           RedirectAttributes ra) {
        workPolicyService.addShiftType(id, name, start, end, off, tone);
        ra.addFlashAttribute("adminMessage", "근무 유형 ‘" + name.strip() + "’을 추가했어요.");
        return "redirect:/admin/schedule/policies/" + id + "#shifts";
    }

    @PostMapping("/{id}/shifts/{sid}")
    public String updateShift(@PathVariable("id") Long id, @PathVariable("sid") Long sid,
                              @RequestParam("name") String name,
                              @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
                              @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end,
                              @RequestParam(value = "off", defaultValue = "false") boolean off,
                              @RequestParam(value = "tone", required = false) Tone tone,
                              RedirectAttributes ra) {
        workPolicyService.updateShiftType(id, sid, name, start, end, off, tone);
        ra.addFlashAttribute("adminMessage", "근무 유형을 고쳤어요.");
        return "redirect:/admin/schedule/policies/" + id + "#shifts";
    }

    @PostMapping("/{id}/shifts/{sid}/delete")
    public String deleteShift(@PathVariable("id") Long id, @PathVariable("sid") Long sid, RedirectAttributes ra) {
        String name = workPolicyService.deleteShiftType(id, sid);
        ra.addFlashAttribute("adminMessage", "근무 유형 ‘" + name + "’을 지웠어요. 이미 배정한 근무는 시간 그대로 남아요.");
        return "redirect:/admin/schedule/policies/" + id + "#shifts";
    }

    @PostMapping("/{id}/members")
    public String assign(@PathVariable("id") Long id,
                         @RequestParam(value = "memberIds", required = false) List<Long> memberIds,
                         RedirectAttributes ra) {
        int n = workPolicyService.assignMembers(id, memberIds);
        ra.addFlashAttribute("adminMessage", n == 0 ? "이미 이 근무제인 직원이에요." : "직원 " + n + "명을 이 근무제로 옮겼어요.");
        return "redirect:/admin/schedule/policies/" + id + "#members";
    }

    // 근무 요일 체크박스용 : 템플릿에서 WorkPolicy.dayLabel 을 쓰기 어려워 모델로
    @ModelAttribute("dayLabels")
    public java.util.Map<DayOfWeek, String> dayLabels() {
        java.util.Map<DayOfWeek, String> m = new java.util.LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) m.put(d, WorkPolicy.dayLabel(d));
        return m;
    }
}
