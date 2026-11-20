package com.mysite.sbb.hr.controller;

import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.hr.constant.DocScope;
import com.mysite.sbb.hr.dto.HrViews.DocRow;
import com.mysite.sbb.hr.service.HrHistoryService;
import com.mysite.sbb.hr.service.HrService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.report.repository.ReportRepository;
import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

// 관리자 : 직원 허브(인사 카드) — 정보 / 근태 / 업무 / 서류 / 이력 탭, 인사 서류 종류 관리
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminHrController {

    private static final Set<String> TABS = Set.of("info", "attendance", "work", "docs", "history");

    private final HrService hrService;
    private final HrHistoryService historyService;
    private final MemberService memberService;
    private final TodoAssignmentRepository todoAssignmentRepository;
    private final ReportRepository reportRepository;

    @GetMapping("/members/{id}")
    public String hub(@PathVariable("id") Long id,
                      @RequestParam(value = "tab", required = false) String tab,
                      @RequestParam(value = "month", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                      Principal principal, Model model) {
        Member m = memberService.getMember(id);
        Member me = memberService.getMember(principal.getName());
        String t = tab != null && TABS.contains(tab) ? tab : "info";
        List<DocRow> docs = hrService.getDocRows(m, me);
        model.addAttribute("m", m);
        model.addAttribute("tab", t);
        model.addAttribute("docs", docs);
        model.addAttribute("missingRequired", docs.stream().filter(DocRow::missingRequired).count());
        switch (t) {
            case "attendance" -> {
                YearMonth ym = month == null ? YearMonth.now() : month;
                model.addAttribute("am", hrService.getAttendanceMonth(m, ym));
            }
            case "work" -> {
                LocalDate to = LocalDate.now(), from = to.minusDays(29);
                long total = todoAssignmentRepository.countByMemberIdAndWorkDateBetween(id, from, to);
                long approved = todoAssignmentRepository.countByMemberIdAndWorkDateBetweenAndStatus(id, from, to, AssignmentStatus.APPROVED);
                long submitted = todoAssignmentRepository.countByMemberIdAndWorkDateBetweenAndStatus(id, from, to, AssignmentStatus.SUBMITTED)
                        + todoAssignmentRepository.countByMemberIdAndWorkDateBetweenAndStatus(id, from, to, AssignmentStatus.PENDING_REVIEW);
                long rework = todoAssignmentRepository.countByMemberIdAndWorkDateBetweenAndStatus(id, from, to, AssignmentStatus.REWORK);
                model.addAttribute("todoTotal", total);
                model.addAttribute("todoDone", approved + submitted);
                model.addAttribute("todoRework", rework);
                model.addAttribute("todoRate", total == 0 ? 0 : Math.round((approved + submitted) * 100.0 / total));
                model.addAttribute("assignments", todoAssignmentRepository.findTop20ByMemberIdOrderByWorkDateDescIdDesc(id));
                model.addAttribute("reports", reportRepository.findTop50ByAuthorIdOrderByIdDesc(id).stream().limit(10).toList());
            }
            case "history" -> model.addAttribute("history", historyService.getHistory(id));
            default -> { }
        }
        return "admin/member-hub";
    }

    @PostMapping("/members/{id}/docs")
    public String upload(@PathVariable("id") Long id, @RequestParam("typeId") Long typeId,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         @RequestParam(value = "expiresAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
                         Principal principal, RedirectAttributes ra) {
        try {
            var doc = hrService.upload(id, typeId, file, expiresAt, memberService.getMember(principal.getName()));
            ra.addFlashAttribute("adminMessage", doc.getType().getName() + " 서류를 올렸어요.");
        } catch (BusinessException | IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/members/" + id + "?tab=docs";
    }

    @PostMapping("/members/{id}/docs/{docId}/delete")
    public String deleteDoc(@PathVariable("id") Long id, @PathVariable("docId") Long docId, Principal principal, RedirectAttributes ra) {
        String name = hrService.deleteDocument(docId, memberService.getMember(principal.getName()));
        ra.addFlashAttribute("adminMessage", name + " 서류를 지웠어요.");
        return "redirect:/admin/members/" + id + "?tab=docs";
    }

    // ---------- 인사 서류 종류 ----------

    @GetMapping("/hr/doc-types")
    public String docTypes(Model model) {
        model.addAttribute("types", hrService.getDocTypes());
        model.addAttribute("counts", hrService.getDocCounts());
        model.addAttribute("scopes", DocScope.values());
        model.addAttribute("activeCount", memberService.getActiveMembers().size());
        return "admin/hr-doc-types";
    }

    @PostMapping("/hr/doc-types")
    public String createType(@RequestParam("name") String name, @RequestParam(value = "description", required = false) String description,
                             @RequestParam(value = "required", defaultValue = "false") boolean required,
                             @RequestParam("scope") DocScope scope,
                             @RequestParam(value = "employeeUpload", defaultValue = "false") boolean employeeUpload,
                             RedirectAttributes ra) {
        hrService.createType(name, description, required, scope, employeeUpload);
        ra.addFlashAttribute("adminMessage", "서류 종류 ‘" + name.strip() + "’을 추가했어요.");
        return "redirect:/admin/hr/doc-types";
    }

    @PostMapping("/hr/doc-types/{id}")
    public String updateType(@PathVariable("id") Long id, @RequestParam("name") String name,
                             @RequestParam(value = "description", required = false) String description,
                             @RequestParam(value = "required", defaultValue = "false") boolean required,
                             @RequestParam("scope") DocScope scope,
                             @RequestParam(value = "employeeUpload", defaultValue = "false") boolean employeeUpload,
                             RedirectAttributes ra) {
        hrService.updateType(id, name, description, required, scope, employeeUpload);
        ra.addFlashAttribute("adminMessage", "서류 종류를 고쳤어요.");
        return "redirect:/admin/hr/doc-types";
    }

    @PostMapping("/hr/doc-types/{id}/toggle")
    public String toggleType(@PathVariable("id") Long id, RedirectAttributes ra) {
        var t = hrService.toggleType(id);
        ra.addFlashAttribute("adminMessage", "‘" + t.getName() + "’ " + (t.isActive() ? "을 다시 사용해요." : "을 사용 중지했어요."));
        return "redirect:/admin/hr/doc-types";
    }
}
