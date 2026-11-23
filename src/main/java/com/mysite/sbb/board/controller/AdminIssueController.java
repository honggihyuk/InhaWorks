package com.mysite.sbb.board.controller;

import com.mysite.sbb.board.constant.IssueCategory;
import com.mysite.sbb.board.dto.IssueFilter;
import com.mysite.sbb.board.service.IssueExportService;
import com.mysite.sbb.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// 관리자 : 이슈 히스토리 내보내기 (현재 필터 그대로). formats = excel 또는 zip
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/issues")
public class AdminIssueController {

    private final IssueExportService exportService;

    @Value("${app.security.bulk-download-enabled:true}")
    private boolean bulkDownloadEnabled;

    @GetMapping("/export")
    public void export(@RequestParam("formats") String formats,
                       @RequestParam(value = "board", required = false) Long board,
                       @RequestParam(value = "place", required = false) Long place,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "category", required = false) IssueCategory category,
                       @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        if (!bulkDownloadEnabled) {
            throw new BusinessException("일괄 다운로드가 꺼져 있어요. (app.security.bulk-download-enabled)");
        }
        if (!request.isUserInRole("ADMIN")) {     // 관리자 체인이 이미 막지만 한 번 더 (심층 방어)
            throw new BusinessException("관리자만 받을 수 있어요.");
        }
        String format = formats.strip().toLowerCase();
        if (!format.equals("excel") && !format.equals("zip")) {
            throw new BusinessException("다운로드 형식은 excel 또는 zip 중 하나를 골라 주세요.");
        }
        IssueFilter filter = new IssueFilter(status, place, category, from, to);
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        log.info("=======> [반출] 이슈 내보내기 {} by {} (board={}, filter={})", format, principal.getName(), board, filter);
        if (format.equals("excel")) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment("이슈내역_" + day + ".xlsx"));
            exportService.writeExcel(board, filter, response.getOutputStream());
        } else {
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment("이슈첨부_" + day + ".zip"));
            exportService.writeZip(board, filter, response.getOutputStream());
        }
    }

    private static String attachment(String filename) {
        return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
    }
}
