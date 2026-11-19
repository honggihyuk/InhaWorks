package com.mysite.sbb.report.service;

import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.report.entity.Report;
import com.mysite.sbb.report.entity.ReportAnswer;
import com.mysite.sbb.report.entity.ReportField;
import com.mysite.sbb.report.entity.ReportForm;
import com.mysite.sbb.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// 관리자 : 양식 하나의 모든 제출 내역 → 엑셀(.xlsx), 첨부 파일 → zip
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportExportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportRepository reportRepository;
    private final FileStore fileStore;

    public void writeExcel(ReportForm form, OutputStream out) throws IOException {
        List<Report> reports = reportRepository.findByFormIdOrderByIdDesc(form.getId());
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("제출 내역");
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row h = sheet.createRow(0);
            String[] fixed = {"번호", "작성자", "팀", "근무지", "제출일시", "상태", "결재 단계"};
            int c = 0;
            for (String s : fixed) cell(h, c++, s, head);
            for (ReportField f : form.getFields()) cell(h, c++, f.getLabel(), head);

            int r = 1;
            for (Report rep : reports) {
                Row row = sheet.createRow(r++);
                int i = 0;
                cell(row, i++, String.valueOf(rep.getId()), null);
                cell(row, i++, rep.getAuthor().getDisplayName(), null);
                cell(row, i++, rep.getAuthor().getTeam() == null ? "" : rep.getAuthor().getTeam().getName(), null);
                cell(row, i++, rep.getAuthor().getWorkplace() == null ? "" : rep.getAuthor().getWorkplace().getName(), null);
                cell(row, i++, rep.getSubmittedAt() == null ? "" : rep.getSubmittedAt().format(DT), null);
                cell(row, i++, rep.getStatus().getLabel(), null);
                cell(row, i++, rep.getApprovals().isEmpty() ? "-" : rep.getCurrentStep() + "/" + rep.getApprovals().size(), null);
                for (ReportField f : form.getFields()) {
                    ReportAnswer a = rep.getAnswer(f.getId());
                    cell(row, i++, a == null ? "" : a.toCellText(), null);
                }
            }
            for (int i = 0; i < fixed.length + form.getFields().size(); i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            wb.write(out);
        }
    }

    // zip 안 파일 이름 : {보고서번호}_{작성자}_{항목}_{원래이름}
    public void writeZip(ReportForm form, OutputStream out) throws IOException {
        List<Report> reports = reportRepository.findByFormIdOrderByIdDesc(form.getId());
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Report rep : reports) {
                for (ReportAnswer a : rep.getAnswers()) {
                    if (!a.hasFile()) continue;
                    Path p = fileStore.resolve(a.getFile().getPath());
                    if (p == null || !Files.exists(p)) continue;
                    String name = safe(rep.getId() + "_" + rep.getAuthor().getDisplayName() + "_" + a.getField().getLabel()
                            + "_" + a.getFile().getOriginalName());
                    if (!names.add(name)) name = a.getId() + "_" + name;
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(p, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }
}
