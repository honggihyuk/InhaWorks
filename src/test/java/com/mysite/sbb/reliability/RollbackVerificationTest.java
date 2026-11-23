package com.mysite.sbb.reliability;

import com.mysite.sbb.attendance.entity.Attendance;
import com.mysite.sbb.attendance.repository.AttendanceRepository;
import com.mysite.sbb.attendance.repository.WorkScheduleRepository;
import com.mysite.sbb.attendance.service.AttendanceService;
import com.mysite.sbb.attendance.service.WorkPolicyService;
import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.PostForm;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.repository.BoardRepository;
import com.mysite.sbb.board.repository.PostRepository;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.hr.entity.HrDocType;
import com.mysite.sbb.hr.entity.HrDocument;
import com.mysite.sbb.hr.repository.HrDocTypeRepository;
import com.mysite.sbb.hr.repository.HrDocumentRepository;
import com.mysite.sbb.hr.service.HrService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import com.mysite.sbb.todo.service.TodoAssignmentGenerator;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import com.mysite.sbb.todo.repository.TodoTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 롤백 검증 : 쓰기 흐름 중간에 예외를 넣어서
//  DB 행이 남지 않는지 · 파일이 남지 않는지(보상) · 기존 파일이 지켜지는지 · Outbox 알림이 생기지 않는지 ·
//  낙관적 락 충돌 때 변경이 없는지 · 배치가 건별로 격리되는지 · 오류 화면이 안전한지 확인한다
//  업로드 폴더는 build/ 아래 테스트 전용 폴더 (공개 / 비공개 분리)
@SpringBootTest(properties = {"app.upload-dir=build/test-uploads", "app.private-dir=build/test-private"})
class RollbackVerificationTest {

    static final Path PUBLIC = Path.of("build/test-uploads").toAbsolutePath();
    static final Path PRIVATE = Path.of("build/test-private").toAbsolutePath();

    @Autowired TransactionTemplate tx;
    @Autowired WebApplicationContext context;
    @Autowired MemberRepository memberRepository;
    @Autowired HrService hrService;
    @Autowired HrDocTypeRepository docTypeRepository;
    @Autowired HrDocumentRepository documentRepository;
    @Autowired BoardService boardService;
    @Autowired BoardRepository boardRepository;
    @Autowired PostRepository postRepository;
    @Autowired AttendanceService attendanceService;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired WorkScheduleRepository workScheduleRepository;
    @Autowired WorkPolicyService workPolicyService;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired FileStore fileStore;
    @Autowired TodoAssignmentGenerator generator;
    @Autowired TodoTaskRepository todoTaskRepository;
    @MockitoSpyBean TodoAssignmentRepository todoAssignmentRepository;

    Member admin, emp;

    @BeforeEach
    void setUp() {
        admin = memberRepository.findByUsername("admin").orElseThrow();
        emp = memberRepository.findByUsername("emp2").orElseThrow();
    }

    // ---------- 파일 + DB (보상) ----------

    @Test
    @DisplayName("① 인사 서류 첫 제출 중 예외 → 서류 행 없음, 비공개 파일도 지워짐")
    void hrUploadRollsBackRowAndFile() throws IOException {
        HrDocType type = type("가족관계증명서");
        long files = count(PRIVATE);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            hrService.upload(emp.getId(), type.getId(), pdf("가족.pdf"), null, admin);
            throw new IllegalStateException("저장 직후 장애 (주입)");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(documentRepository.findByMemberIdAndTypeId(emp.getId(), type.getId())).isEmpty();
        assertThat(count(PRIVATE)).isEqualTo(files);
    }

    @Test
    @DisplayName("② 인사 서류 교체 중 예외 → 예전 서류·예전 파일 그대로, 새 파일은 지워짐 / 정상 교체면 예전 파일만 지워짐")
    void hrReplaceKeepsOldFileOnRollback() throws IOException {
        HrDocType type = type("통장 사본");
        HrDocument first = tx.execute(s -> hrService.upload(emp.getId(), type.getId(), pdf("통장1.pdf"), null, admin));
        Path oldFile = fileStore.resolvePrivate(first.getFile().getPath());
        assertThat(oldFile).exists();
        long files = count(PRIVATE);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            hrService.upload(emp.getId(), type.getId(), pdf("통장2.pdf"), null, admin);
            throw new IllegalStateException("교체 직후 장애 (주입)");
        })).isInstanceOf(IllegalStateException.class);

        HrDocument after = documentRepository.findByMemberIdAndTypeId(emp.getId(), type.getId()).orElseThrow();
        assertThat(after.getFile().getOriginalName()).isEqualTo("통장1.pdf");
        assertThat(oldFile).exists();
        assertThat(count(PRIVATE)).isEqualTo(files);

        // 정상 교체 : 커밋 뒤 예전 파일 삭제, 새 파일 하나
        tx.executeWithoutResult(s -> hrService.upload(emp.getId(), type.getId(), pdf("통장3.pdf"), null, admin));
        assertThat(oldFile).doesNotExist();
        assertThat(count(PRIVATE)).isEqualTo(files);
    }

    @Test
    @DisplayName("③ 이슈 해결 등록 중 두 번째 첨부가 잘못된 형식 → 상태·해결 내용 그대로, 먼저 저장된 첨부 파일 지워짐, 알림 없음")
    void issueResolveIsAtomic() throws IOException {
        Post post = issue();
        long files = count(PUBLIC);
        long outbox = outboxEventRepository.count();

        assertThatThrownBy(() -> boardService.resolveIssue(post.getId(), admin, "해결했어요",
                List.of(pdf("확인서.pdf"), new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[]{1, 2}))))
                .isInstanceOf(IllegalArgumentException.class);

        Post after = postRepository.findById(post.getId()).orElseThrow();
        assertThat(after.getIssueStatus()).isEqualTo(IssueStatus.REPORTED);
        assertThat(after.getResolution()).isNull();
        assertThat(after.getResolvedBy()).isNull();
        assertThat(count(PUBLIC)).isEqualTo(files);
        assertThat(outboxEventRepository.count()).isEqualTo(outbox);
    }

    @Test
    @DisplayName("④ 해결 취소가 커밋되면 해결 첨부 파일도 정리 (고아 파일 없음)")
    void reopenDeletesResolutionFilesAfterCommit() throws IOException {
        Post post = issue();
        long files = count(PUBLIC);
        boardService.resolveIssue(post.getId(), admin, "해결", List.of(pdf("사진.pdf")));
        assertThat(count(PUBLIC)).isEqualTo(files + 1);

        boardService.reopenIssue(post.getId(), admin);
        assertThat(count(PUBLIC)).isEqualTo(files);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getIssueStatus()).isEqualTo(IssueStatus.REVIEWING);
    }

    // ---------- DB + Outbox ----------

    @Test
    @DisplayName("⑤ 스케줄 배정 뒤 같은 트랜잭션에서 예외 → 스케줄 없음, 배정 알림(Outbox)도 없음")
    void scheduleAndOutboxRollBackTogether() {
        LocalDate day = LocalDate.now().plusDays(40);
        long outbox = outboxEventRepository.count();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            attendanceService.addSchedule(emp.getId(), day, LocalTime.of(9, 0), LocalTime.of(18, 0), "롤백", "admin");
            throw new IllegalStateException("배정 직후 장애 (주입)");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(workScheduleRepository.findByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(emp.getId(), day, day)).isEmpty();
        assertThat(outboxEventRepository.count()).isEqualTo(outbox);
    }

    @Test
    @DisplayName("⑥ 사전 검증 실패(끝 ≤ 시작) → 아무것도 저장되지 않음")
    void guardRejectsBeforeWriting() {
        LocalDate day = LocalDate.now().plusDays(41);
        long outbox = outboxEventRepository.count();
        assertThatThrownBy(() -> attendanceService.addSchedule(emp.getId(), day, LocalTime.of(18, 0), LocalTime.of(9, 0), null, "admin"))
                .isInstanceOf(BusinessException.class);
        assertThat(workScheduleRepository.findByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(emp.getId(), day, day)).isEmpty();
        assertThat(outboxEventRepository.count()).isEqualTo(outbox);
    }

    @Test
    @DisplayName("⑦ 소속 직원이 있는 근무 정책 삭제 → 거부, 정책과 근무 유형 그대로")
    void policyDeleteGuard() {
        Long policyId = tx.execute(s -> memberRepository.findByUsername("emp2").orElseThrow().getWorkPolicy().getId());
        int shifts = tx.execute(s -> workPolicyService.getPolicy(policyId).getShiftTypes().size());
        assertThatThrownBy(() -> workPolicyService.delete(policyId)).isInstanceOf(BusinessException.class);
        Integer after = tx.execute(s -> workPolicyService.getPolicy(policyId).getShiftTypes().size());
        assertThat(after).isEqualTo(shifts);
    }

    // ---------- 동시성 ----------

    @Test
    @DisplayName("⑧ 낙관적 락 : 같은 출퇴근 기록을 두 곳에서 고치면 늦은 쪽은 충돌, DB 에는 먼저 저장한 값만 남음")
    void optimisticLockKeepsFirstWrite() {
        LocalDate day = LocalDate.now().minusDays(50);
        Attendance a = attendanceRepository.save(Attendance.builder().member(emp).workDate(day)
                .clockIn(day.atTime(9, 0)).build());
        Attendance tab1 = attendanceRepository.findById(a.getId()).orElseThrow();
        Attendance tab2 = attendanceRepository.findById(a.getId()).orElseThrow();

        tab1.setClockOut(day.atTime(18, 0));
        attendanceRepository.save(tab1);
        tab2.setClockOut(day.atTime(23, 0));
        assertThatThrownBy(() -> attendanceRepository.save(tab2)).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(attendanceRepository.findById(a.getId()).orElseThrow().getClockOut()).isEqualTo(day.atTime(18, 0));
    }

    // ---------- 배치 ----------

    @Test
    @DisplayName("⑨ 배치 건별 격리 : 할 일 하나가 실패해도 다른 할 일 배정은 커밋, 실패는 모아서 알림 → 다시 돌리면 빠진 것만")
    void batchIsolatesFailures() {
        LocalDate day = LocalDate.now().plusDays(60);
        List<Long> taskIds = tx.execute(s -> todoTaskRepository.findActiveOn(day).stream().map(t -> t.getId()).toList());
        assertThat(taskIds).isNotEmpty();
        Long bad = taskIds.get(0);
        doThrow(new IllegalStateException("이 할 일만 장애 (주입)")).when(todoAssignmentRepository).findMemberIds(eq(bad), any());

        assertThatThrownBy(() -> generator.generate(day))
                .isInstanceOf(TodoAssignmentGenerator.BatchPartialFailureException.class)
                .hasMessageContaining("#" + bad);
        assertThat(todoAssignmentRepository.countByTaskIdAndWorkDate(bad, day)).isZero();

        // 장애가 풀린 뒤 다시 실행 → 빠졌던 할 일만 만들어지고, 이미 만든 것은 중복되지 않는다
        org.mockito.Mockito.reset(todoAssignmentRepository);
        long before = todoAssignmentRepository.findByWorkDateBetween(day, day).size();
        generator.generate(day);
        assertThat(todoAssignmentRepository.countByTaskIdAndWorkDate(bad, day)).isPositive();
        long after = todoAssignmentRepository.findByWorkDateBetween(day, day).size();
        assertThat(after).isGreaterThan(before);
        generator.generate(day);
        assertThat(todoAssignmentRepository.findByWorkDateBetween(day, day)).hasSize((int) after);
    }

    // ---------- 전역 예외 응답 ----------

    @Test
    @DisplayName("⑩ 오류 화면은 안전 : 잘못된 주소 400 · 없는 직원 404, 스택 트레이스·예외 이름은 보이지 않음")
    void errorResponsesAreSafe() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/board/abc").with(user("emp2").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("요청이 올바르지 않아요")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
        mvc.perform(get("/admin/members/999999").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at com.mysite"))));
    }

    // ---------- 도우미 ----------

    private HrDocType type(String name) {
        return docTypeRepository.findAllByOrderBySortOrderAscIdAsc().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    private Post issue() {
        Board b = boardRepository.findAllByOrderBySortOrderAscIdAsc().stream().filter(x -> x.getType() == BoardType.ISSUE).findFirst().orElseThrow();
        PostForm f = new PostForm();
        f.setTitle("롤백 테스트 이슈 " + LocalDateTime.now());
        f.setContent("내용");
        return boardService.write(b.getId(), emp, f, List.of());
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ("%PDF-1.4 " + name).getBytes());
    }

    private static long count(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }
}
