package com.mysite.sbb.report.controller;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import com.mysite.sbb.report.constant.ReportCategory;
import com.mysite.sbb.report.constant.ReportFieldType;
import com.mysite.sbb.report.dto.ReportFormForm;
import com.mysite.sbb.report.entity.ReportForm;
import com.mysite.sbb.report.service.ReportExportService;
import com.mysite.sbb.report.service.ReportFormService;
import com.mysite.sbb.report.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

// 관리자 : 보고서 양식 만들기(폼 빌더), 제출 내역, 엑셀 / 첨부 zip 다운로드
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/report")
public class AdminReportController {

    private final ReportFormService reportFormService;
    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final MemberService memberService;
    private final OrganizationService organizationService;

    // [보안] 일괄 다운로드(엑셀·zip) 허용 여부. false 로 두면 관리자도 받을 수 없다 (개인정보 반출 통제)
    @Value("${app.security.bulk-download-enabled:true}")
    private boolean bulkDownloadEnabled;

    @GetMapping
    public String list(@RequestParam(value = "category", required = false) ReportCategory category, Model model) {
        Map<ReportForm, Long> forms = new LinkedHashMap<>();
        reportFormService.getAll().stream()
                .filter(f -> category == null || f.getCategory() == category)
                .forEach(f -> forms.put(f, reportFormService.countReports(f.getId())));
        model.addAttribute("forms", forms);
        model.addAttribute("categories", ReportCategory.values());
        model.addAttribute("category", category);
        return "admin/report-list";
    }

    @GetMapping("/new")
    public String createForm(ReportFormForm reportFormForm, Model model) {
        return form(model);
    }

    @PostMapping("/new")
    public String create(@Valid ReportFormForm reportFormForm, BindingResult bindingResult,
                         Principal principal, Model model, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return form(model);
        }
        try {
            ReportForm form = reportFormService.create(reportFormForm, principal.getName());
            ra.addFlashAttribute("adminMessage", "보고서 양식 '" + form.getTitle() + "' 을 만들었어요.");
            return "redirect:/admin/report/" + form.getId();
        } catch (BusinessException | IllegalArgumentException e) {
            bindingResult.reject("invalid", e.getMessage());
            return form(model);
        }
    }

    // ---------- 양식 수정 ----------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("reportFormForm", ReportFormForm.of(reportFormService.get(id)));
        model.addAttribute("formId", id);
        return form(model);
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id, @Valid ReportFormForm reportFormForm, BindingResult bindingResult,
                       Model model, RedirectAttributes ra) {
        model.addAttribute("formId", id);
        if (bindingResult.hasErrors()) {
            return form(model);
        }
        try {
            ReportForm form = reportFormService.update(id, reportFormForm);
            ra.addFlashAttribute("adminMessage", "보고서 양식 '" + form.getTitle() + "' 을 수정했어요.");
            return "redirect:/admin/report/" + id;
        } catch (BusinessException | IllegalArgumentException e) {
            bindingResult.reject("invalid", e.getMessage());
            return form(model);
        }
    }

    @GetMapping("/{id}")
    public String submissions(@PathVariable("id") Long id, Model model) {
        model.addAttribute("form", reportFormService.get(id));
        model.addAttribute("reports", reportService.getByForm(id));
        model.addAttribute("bulkDownloadEnabled", bulkDownloadEnabled);
        return "admin/report-submissions";
    }

    @PostMapping("/{id}/active")
    public String toggleActive(@PathVariable("id") Long id) {
        reportFormService.toggleActive(id);
        return "redirect:/admin/report";
    }

    @GetMapping("/{id}/excel")
    public void excel(@PathVariable("id") Long id, HttpServletRequest request, HttpServletResponse response,
                      Principal principal) throws IOException {
        checkBulkDownload(request);
        ReportForm form = reportFormService.get(id);
        log.info("=======> 엑셀 다운로드 : {} by {}", form.getTitle(), principal.getName());   // 반출 기록
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment(form.getTitle() + "_" + LocalDate.now() + ".xlsx"));
        reportExportService.writeExcel(form, response.getOutputStream());
    }

    @GetMapping("/{id}/files.zip")
    public void zip(@PathVariable("id") Long id, HttpServletRequest request, HttpServletResponse response,
                    Principal principal) throws IOException {
        checkBulkDownload(request);
        ReportForm form = reportFormService.get(id);
        log.info("=======> 첨부 zip 다운로드 : {} by {}", form.getTitle(), principal.getName());
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment(form.getTitle() + "_첨부_" + LocalDate.now() + ".zip"));
        reportExportService.writeZip(form, response.getOutputStream());
    }

    // 관리자 체인(/admin/**)이 이미 ADMIN 만 통과시키지만, 한 번 더 확인 (심층 방어)
    private void checkBulkDownload(HttpServletRequest request) {
        if (!bulkDownloadEnabled) {
            throw new BusinessException("일괄 다운로드가 꺼져 있어요. (app.security.bulk-download-enabled)");
        }
        if (!request.isUserInRole("ADMIN")) {
            throw new BusinessException("일괄 다운로드는 관리자만 할 수 있어요.");
        }
    }

    private static String attachment(String filename) {
        return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
    }

    private String form(Model model) {
        model.addAttribute("members", memberService.getActiveMembers());
        model.addAttribute("teams", organizationService.getTeams());
        model.addAttribute("workplaces", organizationService.getWorkplaces());
        model.addAttribute("categories", ReportCategory.values());
        model.addAttribute("cycles", RepeatCycle.values());
        model.addAttribute("fieldTypes", ReportFieldType.values());
        return "admin/report-form";
    }
}
