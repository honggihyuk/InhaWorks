package com.mysite.sbb.config;

import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.repository.BoardRepository;
import com.mysite.sbb.board.repository.PostRepository;
import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.entity.Workplace;
import com.mysite.sbb.organization.repository.TeamRepository;
import com.mysite.sbb.organization.repository.WorkplaceRepository;
import com.mysite.sbb.report.constant.ReportCategory;
import com.mysite.sbb.report.constant.ReportFieldType;
import com.mysite.sbb.report.entity.ReportField;
import com.mysite.sbb.report.entity.ReportForm;
import com.mysite.sbb.report.repository.ReportFormRepository;
import com.mysite.sbb.todo.constant.TodoItemType;
import com.mysite.sbb.todo.entity.TodoItem;
import com.mysite.sbb.todo.entity.TodoTask;
import com.mysite.sbb.todo.repository.TodoTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 서버가 처음 시작될 때 기본 데이터를 만든다 (이미 있으면 건너뜀)
//  1) 관리자 계정 (app.admin.*) : 없으면 항상 만든다
//  2) 예시 데이터 (app.seed.enabled=true 이고 팀이 하나도 없을 때만)
//     팀 2, 근무지 2, 직원 3, 할 일 1(매일), 보고서 양식 1(2단계 결재), 게시판 2
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;
    private final WorkplaceRepository workplaceRepository;
    private final TodoTaskRepository todoTaskRepository;
    private final ReportFormRepository reportFormRepository;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin1234}")
    private String adminPassword;

    @Value("${app.admin.email:admin@inhaworks.local}")
    private String adminEmail;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.seed.employee-password:1234}")
    private String employeePassword;

    @Override
    @Transactional
    public void run(String... args) {
        Member admin = memberRepository.findByUsername(adminUsername).orElseGet(() -> {
            log.info("=======> 관리자 계정 생성 : {}", adminUsername);
            return memberRepository.save(Member.builder()
                    .username(adminUsername).name("관리자").password(passwordEncoder.encode(adminPassword))
                    .email(adminEmail).position("대표").role(MemberRole.ADMIN).created(LocalDateTime.now()).build());
        });

        if (!seedEnabled || teamRepository.count() > 0) {
            return;
        }
        log.info("=======> 예시 데이터 만들기 (팀·근무지·직원·할 일·보고서 양식·게시판)");

        Team ops = teamRepository.save(Team.builder().name("운영팀").description("매장 운영·관리").build());
        Team sales = teamRepository.save(Team.builder().name("영업팀").description("영업·고객 응대").build());
        Workplace inha = workplaceRepository.save(Workplace.builder().name("인하점").address("인천 미추홀구 인하로 100").build());
        Workplace songdo = workplaceRepository.save(Workplace.builder().name("송도점").address("인천 연수구 송도과학로 32").build());
        admin.setTeam(ops);
        admin.setWorkplace(inha);

        Member manager = employee("emp1", "김민수", ops, inha, "매니저", MemberRole.MANAGER);
        employee("emp2", "이서연", sales, inha, "사원", MemberRole.USER);
        employee("emp3", "박지훈", sales, songdo, "사원", MemberRole.USER);

        // 할 일 : 매일, 인하점 전체, 검수자 = 매니저
        TodoTask task = TodoTask.builder()
                .title("매장 오픈 체크리스트")
                .guide("매일 오픈 전(09:30까지) 아래 항목을 확인하고 제출해 주세요.\n진열대 사진은 정면에서 한 장 찍어 주세요.")
                .cycle(RepeatCycle.DAILY)
                .startDate(LocalDate.now().minusDays(1))
                .reviewer(manager)
                .createdBy(admin)
                .created(LocalDateTime.now())
                .build();
        task.getWorkplaces().add(inha);
        task.addItem(item(TodoItemType.YES_NO, "출입문·조명을 점검했나요?", null, true));
        task.addItem(item(TodoItemType.CHECKBOX, "POS 정상 작동 확인", null, true));
        task.addItem(item(TodoItemType.PHOTO, "진열대 사진", null, false));
        task.addItem(item(TodoItemType.CHOICE, "매장 청결 상태", "상,중,하", true));
        task.addItem(item(TodoItemType.SIGNATURE, "담당자 서명", null, false));
        task.addItem(item(TodoItemType.TEXT, "특이사항", null, false));
        todoTaskRepository.save(task);

        // 보고서 양식 : 매주 월요일, 영업팀, 결재선 매니저 → 관리자
        ReportForm form = ReportForm.builder()
                .title("주간 업무 보고")
                .description("지난주 실적과 이번 주 계획을 적어 주세요.")
                .category(ReportCategory.TEAM)
                .team(sales)
                .hashtags("#주간보고 #영업팀")
                .cycle(RepeatCycle.WEEKLY)
                .scheduleText("매주 월요일 오전")
                .createdBy(admin)
                .created(LocalDateTime.now())
                .build();
        form.addField(field(ReportFieldType.TEXT, "지난주 주요 실적", null, true));
        form.addField(field(ReportFieldType.TEXT, "이번 주 계획", null, true));
        form.addField(field(ReportFieldType.CHOICE, "목표 달성도", "초과 달성,달성,미달", true));
        form.addField(field(ReportFieldType.YES_NO, "지원이 필요한 일이 있나요?", null, false));
        form.addField(field(ReportFieldType.FILE, "참고 문서", null, false));
        form.addField(field(ReportFieldType.SIGNATURE, "작성자 서명", null, false));
        form.addApprover(manager);
        form.addApprover(admin);
        reportFormRepository.save(form);

        // 게시판 2개 + 고정 공지 1개
        Board notice = boardRepository.save(Board.builder().name("사내 공지").description("회사 소식과 공지사항")
                .type(BoardType.GENERAL).sortOrder(0).created(LocalDateTime.now()).build());
        boardRepository.save(Board.builder().name("이슈 & 해결").description("매장에서 생긴 문제와 해결 방법을 함께 정리해요")
                .type(BoardType.ISSUE).sortOrder(1).created(LocalDateTime.now()).build());
        postRepository.save(Post.builder().board(notice).author(admin).title("InhaWorks 사용 안내")
                .content("출퇴근, 할 일, 보고서, 게시판을 이제 InhaWorks 에서 함께 관리해요.\n궁금한 점은 이 글에 댓글로 남겨 주세요.")
                .pinned(true).submitToken(UUID.randomUUID().toString()).created(LocalDateTime.now()).build());
    }

    private Member employee(String username, String name, Team team, Workplace workplace, String position, MemberRole role) {
        return memberRepository.findByUsername(username).orElseGet(() -> memberRepository.save(Member.builder()
                .username(username).name(name).password(passwordEncoder.encode(employeePassword))
                .team(team).workplace(workplace).position(position).role(role).created(LocalDateTime.now()).build()));
    }

    private static TodoItem item(TodoItemType type, String label, String options, boolean required) {
        return TodoItem.builder().type(type).label(label).options(options).required(required).build();
    }

    private static ReportField field(ReportFieldType type, String label, String options, boolean required) {
        return ReportField.builder().type(type).label(label).options(options).required(required).build();
    }
}
