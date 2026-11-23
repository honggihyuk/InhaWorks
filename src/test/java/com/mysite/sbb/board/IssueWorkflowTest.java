package com.mysite.sbb.board;

import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.constant.IssueCategory;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.IssueFilter;
import com.mysite.sbb.board.dto.IssueSummary;
import com.mysite.sbb.board.dto.PostForm;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.IssueStatusHistory;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.repository.BoardRepository;
import com.mysite.sbb.board.repository.IssueStatusHistoryRepository;
import com.mysite.sbb.board.repository.PostRepository;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.board.service.IssueExportService;
import com.mysite.sbb.board.service.IssueStatusMigration;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.repository.TeamRepository;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 이슈 고도화 : 5단계 상태 · 이력 · 알림 · 데이터 이전 · 내보내기 · 링크 권한
@SpringBootTest(properties = {"app.upload-dir=build/test-uploads-issue", "app.private-dir=build/test-private-issue"})
class IssueWorkflowTest {

    @Autowired BoardService boardService;
    @Autowired IssueExportService exportService;
    @Autowired IssueStatusMigration migration;
    @Autowired BoardRepository boardRepository;
    @Autowired PostRepository postRepository;
    @Autowired IssueStatusHistoryRepository historyRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired WebApplicationContext context;

    Member admin, emp;
    Board board;

    @BeforeEach
    void setUp() {
        admin = memberRepository.findByUsername("admin").orElseThrow();
        emp = memberRepository.findByUsername("emp2").orElseThrow();
        board = boardRepository.findAll().stream().filter(Board::isIssueBoard).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("AC-02 : 검토 → 대기 → 처리 → 해결, 단계마다 이력과 글쓴이 알림이 남는다")
    void fullFlowRecordsHistoryAndNotifies() {
        Post p = write(emp, "단계 흐름");
        long outbox = outboxRepository.count();
        boardService.changeStatus(p.getId(), admin, IssueStatus.REVIEWING, "확인 중", null);
        boardService.changeStatus(p.getId(), admin, IssueStatus.WAITING, null, null);
        boardService.changeStatus(p.getId(), admin, IssueStatus.IN_PROGRESS, null, null);
        boardService.changeStatus(p.getId(), admin, IssueStatus.RESOLVED, null, "인근 매장 지원");

        List<IssueStatusHistory> history = boardService.getHistory(p.getId());
        assertThat(history).extracting(IssueStatusHistory::getToStatus).containsExactly(IssueStatus.REPORTED,
                IssueStatus.REVIEWING, IssueStatus.WAITING, IssueStatus.IN_PROGRESS, IssueStatus.RESOLVED);
        assertThat(history.get(1).getMemo()).isEqualTo("확인 중");
        assertThat(outboxRepository.count()).isEqualTo(outbox + 4);
        Post after = postRepository.findById(p.getId()).orElseThrow();
        assertThat(after.getResolution()).isEqualTo("인근 매장 지원");
        assertThat(after.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("해결 내용 없이는 해결 완료 불가, 상태도에 없는 이동 불가, 직원은 칩 메뉴 사용 불가")
    void guards() {
        Post p = write(emp, "가드");
        assertThatThrownBy(() -> boardService.changeStatus(p.getId(), admin, IssueStatus.IN_PROGRESS, null, null))
                .isInstanceOf(BusinessException.class);
        boardService.changeStatus(p.getId(), admin, IssueStatus.REVIEWING, null, null);
        assertThatThrownBy(() -> boardService.changeStatus(p.getId(), admin, IssueStatus.RESOLVED, null, "  "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> boardService.changeStatus(p.getId(), emp, IssueStatus.WAITING, null, null))
                .isInstanceOf(NoAccessException.class);
        assertThat(postRepository.findById(p.getId()).orElseThrow().getIssueStatus()).isEqualTo(IssueStatus.REVIEWING);
    }

    @Test
    @DisplayName("기존 엔드포인트 호환 : 처리 시작 → 해결 → 해결 취소는 검토 중으로")
    void legacyActions() {
        Post p = write(emp, "기존 버튼");
        boardService.startIssue(p.getId(), emp);
        assertThat(statusOf(p)).isEqualTo(IssueStatus.IN_PROGRESS);
        boardService.resolveIssue(p.getId(), emp, "해결", null);
        assertThat(statusOf(p)).isEqualTo(IssueStatus.RESOLVED);
        boardService.reopenIssue(p.getId(), admin);
        assertThat(statusOf(p)).isEqualTo(IssueStatus.REVIEWING);
        assertThat(postRepository.findById(p.getId()).orElseThrow().getResolution()).isNull();
        assertThat(IssueStatus.parse("OPEN")).isEqualTo(IssueStatus.REPORTED);
    }

    @Test
    @DisplayName("ISS-04 : 예전 OPEN 데이터는 REPORTED 로, 다시 돌려도 결과가 같다")
    void migrationIsIdempotent() throws Exception {
        Post p = write(emp, "이전 대상");
        Post keep = write(emp, "처리 중 유지");
        boardService.startIssue(keep.getId(), emp);
        jdbc.update("update post set issue_status = 'OPEN' where id = ?", p.getId());

        migration.afterPropertiesSet();
        migration.afterPropertiesSet();

        assertThat(statusOf(p)).isEqualTo(IssueStatus.REPORTED);
        assertThat(statusOf(keep)).isEqualTo(IssueStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("ISS-05 : 처리 대기 중 통계는 해결 완료 외 모든 단계를 합산한다")
    void pendingSummaryCountsAllOpenSteps() {
        IssueSummary before = boardService.issueSummary(board, null);
        Post a = write(emp, "통계1");
        Post b = write(emp, "통계2");
        boardService.changeStatus(a.getId(), admin, IssueStatus.REVIEWING, null, null);
        boardService.changeStatus(a.getId(), admin, IssueStatus.WAITING, null, null);
        boardService.resolveIssue(b.getId(), admin, "끝", null);
        IssueSummary after = boardService.issueSummary(board, null);
        assertThat(after.pending()).isEqualTo(before.pending() + 1);
        assertThat(after.resolved()).isEqualTo(before.resolved() + 1);
    }

    @Test
    @DisplayName("AC-03 : Excel 은 필터와 같은 건수, 해결 소요 일·이력 포함")
    void excelFollowsFilter() throws Exception {
        String title = "엑셀-" + UUID.randomUUID();
        Post p = write(emp, title);
        boardService.changeStatus(p.getId(), admin, IssueStatus.REVIEWING, "메모", null);
        boardService.changeStatus(p.getId(), admin, IssueStatus.RESOLVED, null, "엑셀 해결");
        IssueFilter filter = new IssueFilter("RESOLVED", emp.getWorkplace() == null ? null : tx.execute(s ->
                memberRepository.findById(emp.getId()).orElseThrow().getWorkplace().getId()), null, LocalDate.now(), LocalDate.now());
        int expected = tx.execute(s -> exportService.find(board.getId(), filter).size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.writeExcel(board.getId(), filter, out);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(expected);
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("해결 소요 일");
            boolean found = false;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (title.equals(sheet.getRow(i).getCell(1).getStringCellValue())) {
                    found = true;
                    assertThat(sheet.getRow(i).getCell(6).getStringCellValue()).isEqualTo("해결 완료");
                    assertThat(sheet.getRow(i).getCell(7).getStringCellValue()).contains("보고 완료 → 검토 중").contains("메모");
                    assertThat(sheet.getRow(i).getCell(11).getStringCellValue()).isEqualTo("엑셀 해결");
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    @DisplayName("AC-04 : Zip 은 이슈별 폴더의 이슈/해결 아래에 첨부 원본이 들어간다 (경로 조작 이름도 안전)")
    void zipHasIssueFolders() throws Exception {
        PostForm form = form("../위험한/제목");
        Post p = boardService.write(board.getId(), emp, form, List.of(pdfFile("../../evil.pdf")));
        boardService.resolveIssue(p.getId(), admin, "해결", List.of(pdfFile("해결.pdf")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.writeZip(board.getId(), new IssueFilter("RESOLVED", null, null, null, null), out);
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) names.add(e.getName());
        }
        assertThat(names).anyMatch(n -> n.startsWith(p.getId() + "_") && n.contains("/이슈/"));
        assertThat(names).anyMatch(n -> n.startsWith(p.getId() + "_") && n.endsWith("/해결/해결.pdf"));
        assertThat(names).noneMatch(n -> n.contains("../") || n.startsWith("/"));
    }

    @Test
    @DisplayName("AC-05 · ISS-15 : 다른 팀 이슈 링크는 403 안내, 첨부 직접 주소도 차단, 내보내기는 관리자만")
    void accessControl() throws Exception {
        Team other = teamRepository.save(Team.builder().name("보안팀-" + UUID.randomUUID().toString().substring(0, 8)).build());
        Board secret = boardService.createBoard("팀 이슈-" + other.getId(), null, BoardType.ISSUE, other.getId());
        Post p = boardService.write(secret.getId(), admin, form("팀 전용 이슈"), List.of(pdfFile("비밀.pdf")));
        String path = tx.execute(s -> postRepository.findById(p.getId()).orElseThrow().getFiles().get(0).getPath());
        String stored = path.substring("/uploads/".length());
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(get("/board/post/" + p.getId()).with(user("emp2").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/board/post/" + p.getId() + "/files/" + stored).with(user("emp2").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("emp2").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/board/post/" + p.getId() + "/files/" + stored).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/issues/export").param("formats", "excel").with(user("emp2").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/issues/export").param("formats", "excel").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(new IssueFilter("OPEN", null, null, null, null).status()).isEqualTo(IssueFilter.PENDING);   // 예전 주소 호환
    }

    private IssueStatus statusOf(Post p) {
        return postRepository.findById(p.getId()).orElseThrow().getIssueStatus();
    }

    private Post write(Member author, String title) {
        return boardService.write(board.getId(), author, form(title), null);
    }

    private PostForm form(String title) {
        PostForm f = new PostForm();
        f.setTitle(title);
        f.setContent("내용");
        f.setCategory(IssueCategory.ETC);
        f.setSubmitToken(UUID.randomUUID().toString());
        return f;
    }

    private static MockMultipartFile pdfFile(String name) {
        return new MockMultipartFile("files", name, "application/pdf", new byte[]{1, 2, 3});
    }
}
