package com.mysite.sbb.report.controller;

import com.mysite.sbb.common.dto.AnswerInput;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.report.constant.ReportCategory;
import com.mysite.sbb.report.entity.Report;
import com.mysite.sbb.report.entity.ReportField;
import com.mysite.sbb.report.entity.ReportForm;
import com.mysite.sbb.report.service.ReportFormService;
import com.mysite.sbb.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

// 직원 : 보고서 쓰기 / 내 보고서, 결재자 : 결재함 → 승인 / 반려
@Controller
@RequiredArgsConstructor
public class ReportController {

    private final MemberService memberService;
    private final ReportFormService reportFormService;
    private final ReportService reportService;

    @GetMapping("/report")
    public String list(@RequestParam(value = "category", required = false) ReportCategory category,
                       @RequestParam(value = "tag", required = false) String tag,
                       Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        List<ReportForm> all = reportFormService.getWritable(me, null, null);
        // 해시태그 목록 (필터 칩)
        Set<String> tags = new TreeSet<>();
        all.forEach(f -> tags.addAll(f.getTagList()));
        model.addAttribute("forms", reportFormService.getWritable(me, category, tag));
        model.addAttribute("tags", tags);
        model.addAttribute("categories", ReportCategory.values());
        model.addAttribute("category", category);
        model.addAttribute("tag", tag);
        model.addAttribute("myReports", reportService.getMine(me));
        model.addAttribute("inboxCount", reportService.getInbox(me).size());
        return "report/list";
    }

    @GetMapping("/report/write/{formId}")
    public String writeForm(@PathVariable("formId") Long formId, Principal principal, Model model) {
        ReportForm form = reportFormService.get(formId);
        model.addAttribute("form", form);
        model.addAttribute("canWrite", form.canWrite(memberService.getMember(principal.getName())));
        model.addAttribute("submitToken", UUID.randomUUID().toString());
        return "report/write";
    }

    // 항목별 입력 이름 : field_{ID} (글자 값), file_{ID} (이미지·문서), sig_{ID} (서명)
    @PostMapping("/report/write/{formId}")
    public String write(@PathVariable("formId") Long formId, MultipartHttpServletRequest request,
                        Principal principal, RedirectAttributes ra) {
        ReportForm form = reportFormService.get(formId);
        Map<Long, AnswerInput> inputs = new HashMap<>();
        for (ReportField f : form.getActiveFields()) {
            String[] values = request.getParameterValues("field_" + f.getId());
            inputs.put(f.getId(), new AnswerInput(values == null ? List.of() : Arrays.asList(values),
                    request.getFile("file_" + f.getId()), request.getParameter("sig_" + f.getId())));
        }
        try {
            Report report = reportService.submit(formId, principal.getName(), inputs, request.getParameter("submitToken"));
            ra.addFlashAttribute("reportMessage", "보고서를 제출했어요.");
            return "redirect:/report/" + report.getId();
        } catch (DataIntegrityViolationException e) {
            // 같은 토큰으로 동시에 두 번 제출 → 먼저 들어간 것만 저장됨
            ra.addFlashAttribute("errorMessage", "이미 제출된 보고서예요.");
            return "redirect:/report";
        }
    }

    @GetMapping("/report/{id}")
    public String detail(@PathVariable("id") Long id, Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        Report r = reportService.getForView(id, me);
        model.addAttribute("r", r);
        model.addAttribute("canDecide", reportService.canDecide(r, me));
        model.addAttribute("canUndoApprove", reportService.undoableApproval(r, me) != null);
        model.addAttribute("comments", reportService.getComments(id));
        return "report/detail";
    }

    @PostMapping("/report/{id}/decide")
    public String decide(@PathVariable("id") Long id, @RequestParam("decision") String decision,
                         @RequestParam(value = "comment", required = false) String comment,
                         Principal principal, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        reportService.decide(id, principal.getName(), approve, comment);
        ra.addFlashAttribute("reportMessage", approve ? "승인했어요." : "반려했어요.");
        if (approve) {
            ra.addFlashAttribute("undoUrl", "/report/" + id + "/decide/undo");   // 토스트의 '실행 취소'
        }
        return "redirect:/report/" + id;
    }

    // 승인 실행 취소 (10분 안, 다음 결재자가 처리하기 전)
    @PostMapping("/report/{id}/decide/undo")
    public String undoApprove(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        reportService.undoApprove(id, principal.getName());
        ra.addFlashAttribute("reportMessage", "승인을 취소했어요. 다시 내 결재 차례예요.");
        return "redirect:/report/" + id;
    }

    @PostMapping("/report/{id}/comment")
    public String comment(@PathVariable("id") Long id, @RequestParam("content") String content, Principal principal) {
        reportService.addComment(id, principal.getName(), content);
        return "redirect:/report/" + id + "#comments";
    }

    // 결재함
    @GetMapping("/approvals")
    public String approvals(Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        model.addAttribute("inbox", reportService.getInbox(me));
        model.addAttribute("decided", reportService.getDecided(me));
        return "report/approvals";
    }
}
