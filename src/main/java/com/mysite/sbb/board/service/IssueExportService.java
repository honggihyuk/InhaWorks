package com.mysite.sbb.board.service;

import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.IssueFilter;
import com.mysite.sbb.board.entity.IssueStatusHistory;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.repository.BoardRepository;
import com.mysite.sbb.board.repository.IssueStatusHistoryRepository;
import com.mysite.sbb.board.repository.PostRepository;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.file.FileStore;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// 관리자 : 이슈 히스토리 내보내기 (현재 필터 그대로) → Excel(.xlsx) / 첨부 원본 Zip
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueExportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int CELL_LIMIT = 32000;   // 엑셀 한 칸 최대 32,767자

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final IssueStatusHistoryRepository historyRepository;
    private final FileStore fileStore;

    // boardId 가 없으면 모든 이슈 게시판
    public List<Post> find(Long boardId, IssueFilter filter) {
        return boardRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(b -> b.getType() == BoardType.ISSUE && (boardId == null || b.getId().equals(boardId)))
                .flatMap(b -> postRepository.findByBoardIdOrderByPinnedDescIdDesc(b.getId()).stream())
                .filter(filter::matches)
                .toList();
    }

    public void writeExcel(Long boardId, IssueFilter filter, OutputStream out) throws IOException {
        List<Post> posts = find(boardId, filter);
        Map<Long, List<IssueStatusHistory>> history = posts.isEmpty() ? Map.of()
                : historyRepository.findTimelines(posts.stream().map(Post::getId).toList()).stream()
                .collect(Collectors.groupingBy(h -> h.getPost().getId()));
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("이슈 내역");
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] columns = {"번호", "제목", "분류", "지점", "작성자", "등록 시각", "현재 상태", "상태 변경 이력",
                    "해결자", "해결 시각", "해결 소요 일", "해결 내용"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) cell(h, i, columns[i], head);

            int r = 1;
            for (Post p : posts) {
                boolean done = p.getIssueStatus() == IssueStatus.RESOLVED;
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(p.getId());
                cell(row, c++, p.getTitle(), null);
                cell(row, c++, p.getCategory() == null ? "" : p.getCategory().getLabel(), null);
                cell(row, c++, p.getWorkplace() == null ? "" : p.getWorkplace().getName(), null);
                cell(row, c++, p.getAuthor() == null ? "" : p.getAuthor().getDisplayName(), null);
                cell(row, c++, p.getCreated() == null ? "" : p.getCreated().format(DT), null);
                cell(row, c++, p.getIssueStatus() == null ? "" : p.getIssueStatus().getLabel(), null);
                cell(row, c++, historyText(history.getOrDefault(p.getId(), List.of())), null);
                cell(row, c++, done && p.getResolvedBy() != null ? p.getResolvedBy().getDisplayName() : "", null);
                cell(row, c++, done && p.getResolvedAt() != null ? p.getResolvedAt().format(DT) : "", null);
                if (done && p.getResolvedAt() != null) row.createCell(c).setCellValue(p.getElapsedDays());
                c++;
                cell(row, c, done ? p.getResolution() : "", null);
            }
            int[] widths = {8, 36, 10, 12, 12, 17, 12, 48, 12, 17, 12, 48};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0, 1);
            wb.write(out);
        }
    }

    // zip 구조 : {번호}_{제목}/이슈/파일, {번호}_{제목}/해결/파일
    public void writeZip(Long boardId, IssueFilter filter, OutputStream out) throws IOException {
        List<Post> posts = find(boardId, filter);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Post p : posts) {
                String folder = safe(p.getId() + "_" + p.getTitle());
                addFiles(zip, folder + "/이슈/", p.getFiles());
                addFiles(zip, folder + "/해결/", p.getResolutionFiles());
            }
        }
    }

    private void addFiles(ZipOutputStream zip, String prefix, List<FileRef> files) throws IOException {
        Set<String> used = new HashSet<>();
        int n = 0;
        for (FileRef f : files) {
            n++;
            Path path = fileStore.resolve(f.getPath());
            if (path == null || !Files.isRegularFile(path)) continue;
            String name = safe(f.getOriginalName() == null ? path.getFileName().toString() : f.getOriginalName());
            if (!used.add(name)) name = n + "_" + name;
            used.add(name);
            zip.putNextEntry(new ZipEntry(prefix + name));
            Files.copy(path, zip);
            zip.closeEntry();
        }
    }

    private static String historyText(List<IssueStatusHistory> list) {
        StringBuilder sb = new StringBuilder();
        for (IssueStatusHistory h : list) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(h.getChangedAt().format(DT)).append(' ');
            if (h.getFromStatus() != null) sb.append(h.getFromStatus().getLabel()).append(" → ");
            sb.append(h.getToStatus().getLabel());
            if (h.getChangedBy() != null) sb.append(" (").append(h.getChangedBy().getDisplayName()).append(')');
            if (h.getMemo() != null) sb.append(" : ").append(h.getMemo());
        }
        return sb.toString();
    }

    // zip 안 이름 : 경로 구분자·제어 문자를 없애고, 점으로만 된 이름(.., .)이 되지 않게 한다
    static String safe(String s) {
        String v = s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\s]+", "_").replaceAll("^\\.+", "_");
        if (v.isBlank()) v = "file";
        return v.length() > 120 ? v.substring(v.length() - 120) : v;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        String v = value == null ? "" : value;
        cell.setCellValue(v.length() > CELL_LIMIT ? v.substring(0, CELL_LIMIT) + "…" : v);   // 문자열 칸 : 수식으로 실행되지 않는다
        if (style != null) cell.setCellStyle(style);
    }
}
