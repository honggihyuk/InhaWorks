package com.mysite.sbb.todo.service;

import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.outbox.service.OutboxService;
import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.constant.TodoItemType;
import com.mysite.sbb.common.dto.AnswerInput;
import com.mysite.sbb.todo.dto.TaskDashboard;
import com.mysite.sbb.todo.entity.*;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import com.mysite.sbb.todo.repository.TodoCommentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

// 직원별 할 일 : 오늘 목록, 제출, 검수(통과 / 재작업), 댓글, 대시보드, 푸시, 마감 알림
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TodoAssignmentService {

    private static final List<AssignmentStatus> INCOMPLETE = List.of(AssignmentStatus.NOT_SUBMITTED, AssignmentStatus.REWORK);

    private final TodoAssignmentRepository todoAssignmentRepository;
    private final TodoCommentRepository todoCommentRepository;
    private final MemberService memberService;
    private final FileStore fileStore;
    private final OutboxService outboxService;

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public List<TodoAssignment> getMine(Member me, LocalDate date) {
        return todoAssignmentRepository.findByMemberIdAndWorkDateOrderByIdAsc(me.getId(), date);
    }

    // 지난 날짜에 못 낸 것 / 재작업 필요한 것
    @Transactional(readOnly = true)
    public List<TodoAssignment> getMyOverdue(Member me, LocalDate today) {
        return todoAssignmentRepository.findByMemberIdAndWorkDateBeforeAndStatusInOrderByWorkDateDesc(me.getId(), today, INCOMPLETE);
    }

    @Transactional(readOnly = true)
    public long countMyIncomplete(Member me, LocalDate date) {
        return todoAssignmentRepository.countByMemberIdAndWorkDateAndStatusIn(me.getId(), date, INCOMPLETE);
    }

    @Transactional(readOnly = true)
    public TodoAssignment getForView(Long id, Member me) {
        TodoAssignment a = get(id);
        boolean owner = a.getMember().getId().equals(me.getId());
        if (!owner && !isReviewer(a, me) && !me.isAdmin()) {
            throw new BusinessException("이 할 일을 볼 수 있는 권한이 없어요.");
        }
        return a;
    }

    public boolean canSubmit(TodoAssignment a, Member me) {
        return a.getMember().getId().equals(me.getId()) && a.getStatus().isSubmittable()
                && !a.getWorkDate().isAfter(LocalDate.now());
    }

    public boolean canReview(TodoAssignment a, Member me) {
        return a.getStatus() == AssignmentStatus.PENDING_REVIEW && (isReviewer(a, me) || me.isAdmin());
    }

    // 검수 대기함 : 내가 검수자인 것 (관리자는 전체)
    @Transactional(readOnly = true)
    public List<TodoAssignment> getReviewQueue(Member me) {
        return me.isAdmin()
                ? todoAssignmentRepository.findByStatusOrderByWorkDateDescIdDesc(AssignmentStatus.PENDING_REVIEW)
                : todoAssignmentRepository.findByTaskReviewerIdAndStatusOrderByWorkDateDescIdDesc(me.getId(), AssignmentStatus.PENDING_REVIEW);
    }

    @Transactional(readOnly = true)
    public long countReviewWaiting(Member me) {
        return todoAssignmentRepository.countByTaskReviewerIdAndStatus(me.getId(), AssignmentStatus.PENDING_REVIEW);
    }

    @Transactional(readOnly = true)
    public List<TodoComment> getComments(Long assignmentId) {
        return todoCommentRepository.findByAssignmentIdOrderByIdAsc(assignmentId);
    }

    // ---------- 제출 ----------

    // [신뢰성]
    //  - 중복 제출 방지 : 화면을 열 때 만든 submitToken 이 지난번 제출과 같으면 (더블클릭, 새로고침 재전송) 그냥 무시
    //  - @Version 충돌(같은 할 일을 두 탭에서 동시에 제출) 이나 일시적인 DB 오류면 @Retryable 이 다시 시도한다.
    //    다시 시도할 때는 새 트랜잭션이므로 최신 상태를 읽고, 이미 같은 토큰으로 제출됐으면 조용히 끝난다.
    //  - 사진·서명 파일은 트랜잭션이 롤백되면 FileStore 가 지운다 (보상)
    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public TodoAssignment submit(Long id, String username, Map<Long, AnswerInput> inputs, String submitToken) {
        Member me = memberService.getMember(username);
        TodoAssignment a = get(id);
        if (submitToken != null && submitToken.equals(a.getLastSubmitToken())) {
            log.info("=======> 같은 제출 토큰 → 중복 제출 무시 (assignment {})", id);
            return a;
        }
        if (!canSubmit(a, me)) {
            throw new BusinessException("지금은 제출할 수 없어요. (" + a.getStatus().getLabel() + ")");
        }
        for (TodoItem item : a.getTask().getActiveItems()) {
            AnswerInput in = inputs.getOrDefault(item.getId(), new AnswerInput(List.of(), null, null));
            TodoAnswer answer = a.answerOf(item).orElse(null);
            boolean hadFile = answer != null && answer.hasFile();
            String value = in.joinedValue();

            // 필수 항목 확인
            if (item.isRequired()) {
                boolean ok = switch (item.getType()) {
                    case PHOTO -> in.hasFile() || hadFile;
                    case SIGNATURE -> in.hasSignature() || hadFile;
                    default -> value != null;
                };
                if (!ok) throw new BusinessException("필수 항목을 채워 주세요 : " + item.getLabel());
            }
            if (item.getType() == TodoItemType.CHOICE && value != null && !item.getOptionList().contains(value)) {
                throw new BusinessException("선택지에 없는 값이에요 : " + item.getLabel());
            }

            if (answer == null) {
                answer = TodoAnswer.builder().assignment(a).item(item).build();
                a.getAnswers().add(answer);
            }
            if (item.getType() == TodoItemType.PHOTO) {
                FileRef ref = fileStore.storeImage(in.file());
                if (ref != null) answer.setFile(ref);
            } else if (item.getType() == TodoItemType.SIGNATURE) {
                FileRef ref = fileStore.storeSignature(in.signature());
                if (ref != null) answer.setFile(ref);
            } else {
                answer.setValue(value);
            }
        }
        boolean needsReview = a.getTask().getReviewer() != null;
        a.setStatus(needsReview ? AssignmentStatus.PENDING_REVIEW : AssignmentStatus.SUBMITTED);
        a.setSubmittedAt(LocalDateTime.now());
        a.setSubmitCount(a.getSubmitCount() + 1);
        a.setLastSubmitToken(submitToken);

        if (needsReview) {
            outboxService.notify(List.of(a.getTask().getReviewer()), NotificationType.TODO,
                    "검수 요청 : " + a.getTask().getTitle(),
                    me.getDisplayName() + " 님이 " + a.getWorkDate() + " 할 일을 제출했어요.",
                    "/todo/" + a.getId(), "TodoAssignment", a.getId(), null);
        }
        return a;
    }

    // ---------- 검수 ----------

    // [신뢰성] 동시 검수 : 검수자와 관리자가 동시에 누르면 @Version 때문에 늦은 쪽 커밋이 실패(낙관적 락 충돌)한다.
    //  @Retryable 이 새 트랜잭션으로 다시 실행 → 최신 상태(이미 검수 통과)를 읽고 "이미 처리됨" 으로 안내.
    //  (재시도 프록시가 트랜잭션 프록시보다 바깥에 있어야 매번 새 트랜잭션이 된다 → ResilienceConfig 의 order)
    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public TodoAssignment review(Long id, String username, boolean approve, String reason) {
        Member me = memberService.getMember(username);
        TodoAssignment a = get(id);
        if (a.getStatus() != AssignmentStatus.PENDING_REVIEW) {
            throw new BusinessException("이미 처리된 할 일이에요. (" + a.getStatus().getLabel() + ")");
        }
        if (!canReview(a, me)) {
            throw new BusinessException("이 할 일의 검수자가 아니에요.");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw new BusinessException("재작업 요청 사유를 입력해 주세요.");
        }
        a.setStatus(approve ? AssignmentStatus.APPROVED : AssignmentStatus.REWORK);
        a.setReviewedBy(me);
        a.setReviewedAt(LocalDateTime.now());
        a.setReworkReason(approve ? null : reason.strip());

        outboxService.notify(List.of(a.getMember()), NotificationType.TODO,
                (approve ? "검수 통과 : " : "재작업 요청 : ") + a.getTask().getTitle(),
                approve ? a.getWorkDate() + " 할 일이 검수를 통과했어요." : "사유 : " + reason.strip(),
                "/todo/" + a.getId(), "TodoAssignment", a.getId(), null);
        return a;
    }

    // ---------- 검수 통과 실행 취소 ----------
    //  검수 통과를 누른 사람(또는 관리자)이 UNDO_WINDOW 안에만 되돌릴 수 있다 → 다시 '검수 대기중'
    //  직원에게 이미 간 '검수 통과' 알림은 되돌릴 수 없으므로 '취소됐어요' 알림을 한 번 더 보낸다
    public static final Duration UNDO_WINDOW = Duration.ofMinutes(10);

    public boolean canUndoReview(TodoAssignment a, Member me) {
        return a.getStatus() == AssignmentStatus.APPROVED
                && a.getReviewedBy() != null && a.getReviewedAt() != null
                && (a.getReviewedBy().getId().equals(me.getId()) || me.isAdmin())
                && a.getReviewedAt().plus(UNDO_WINDOW).isAfter(LocalDateTime.now());
    }

    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public TodoAssignment undoReview(Long id, String username) {
        Member me = memberService.getMember(username);
        TodoAssignment a = get(id);
        if (!canUndoReview(a, me)) {
            throw new BusinessException("되돌릴 수 없어요. 검수 통과 후 " + UNDO_WINDOW.toMinutes()
                    + "분 안에, 검수한 사람만 취소할 수 있어요. (" + a.getStatus().getLabel() + ")");
        }
        a.setStatus(AssignmentStatus.PENDING_REVIEW);
        a.setReviewedBy(null);
        a.setReviewedAt(null);
        outboxService.notify(List.of(a.getMember()), NotificationType.TODO,
                "검수 통과 취소 : " + a.getTask().getTitle(),
                me.getDisplayName() + " 님이 검수 통과를 취소했어요. 다시 검수 대기중이에요.",
                "/todo/" + a.getId(), "TodoAssignment", a.getId(), null);
        return a;
    }

    // ---------- 댓글 (@멘션) ----------

    public void addComment(Long id, String username, String content) {
        if (content == null || content.isBlank()) return;
        Member me = memberService.getMember(username);
        TodoAssignment a = getForView(id, me);
        todoCommentRepository.save(TodoComment.builder()
                .assignment(a).author(me).content(content.strip()).created(LocalDateTime.now()).build());

        String link = "/todo/" + a.getId();
        List<Member> mentioned = memberService.findMentioned(content).stream()
                .filter(m -> !m.getId().equals(me.getId())).toList();
        outboxService.notify(mentioned, NotificationType.MENTION, me.getDisplayName() + " 님이 회원님을 언급했어요",
                a.getTask().getTitle() + " : " + content.strip(), link, "TodoAssignment", a.getId(), null);

        // 담당자 / 검수자 에게 새 댓글 알림 (나와 멘션 받은 사람은 빼고)
        Set<Long> skip = new HashSet<>();
        skip.add(me.getId());
        mentioned.forEach(m -> skip.add(m.getId()));
        List<Member> others = new ArrayList<>();
        for (Member m : Arrays.asList(a.getMember(), a.getTask().getReviewer())) {
            if (m != null && skip.add(m.getId())) others.add(m);
        }
        outboxService.notify(others, NotificationType.TODO, "새 댓글 : " + a.getTask().getTitle(),
                me.getDisplayName() + " : " + content.strip(), link, "TodoAssignment", a.getId(), null);
    }

    // ---------- 관리자 : 대시보드 / 푸시 ----------

    @Transactional(readOnly = true)
    public TaskDashboard dashboard(TodoTask task, LocalDate date) {
        List<TodoAssignment> list = todoAssignmentRepository.findByTaskIdAndWorkDateOrderByIdAsc(task.getId(), date);
        Map<AssignmentStatus, Long> counts = new EnumMap<>(AssignmentStatus.class);
        for (AssignmentStatus s : AssignmentStatus.values()) counts.put(s, 0L);
        list.forEach(a -> counts.merge(a.getStatus(), 1L, Long::sum));
        long done = list.stream().filter(a -> a.getStatus().isDone()).count();
        int rate = list.isEmpty() ? 0 : (int) Math.round(done * 100.0 / list.size());
        return new TaskDashboard(task, date, list, counts, list.size(), done, rate);
    }

    // '푸시' : 미완료(제출 미완료 / 재작업 필요) 직원에게만 다시 알림
    // [신뢰성] 멱등성 키 = "remind:{화면토큰}:{할일ID}" → 버튼을 두 번 눌러도 사람마다 알림은 한 번
    public int remind(Long taskId, LocalDate date, String token) {
        int sent = 0;
        for (TodoAssignment a : todoAssignmentRepository.findByTaskIdAndWorkDateOrderByIdAsc(taskId, date)) {
            if (!a.getStatus().isSubmittable()) continue;
            if (outboxService.notify(List.of(a.getMember()), NotificationType.TODO,
                    "[다시 알림] " + a.getTask().getTitle(),
                    a.getWorkDate() + " 할 일을 아직 완료하지 않았어요. " + (a.getStatus() == AssignmentStatus.REWORK ? "(재작업 필요)" : ""),
                    "/todo/" + a.getId(), "TodoAssignment", a.getId(), "remind:" + token + ":" + a.getId()) != null) {
                sent++;
            }
        }
        return sent;
    }

    // 마감 알림 배치 : 그 날짜의 미완료 할 일 담당자에게 (멱등성 키 = 날짜 + 할 일 ID → 하루에 한 번만)
    public int notifyDeadline(LocalDate date) {
        int sent = 0;
        for (TodoAssignment a : todoAssignmentRepository.findByWorkDateAndStatusIn(date, INCOMPLETE)) {
            if (outboxService.notify(List.of(a.getMember()), NotificationType.TODO,
                    "[마감 임박] " + a.getTask().getTitle(), "오늘 할 일 마감 시간이에요. 아직 완료하지 않았어요.",
                    "/todo/" + a.getId(), "TodoAssignment", a.getId(), "deadline:" + date + ":" + a.getId()) != null) {
                sent++;
            }
        }
        return sent;
    }

    private TodoAssignment get(Long id) {
        return todoAssignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("할 일을 찾을 수 없어요."));
    }

    private boolean isReviewer(TodoAssignment a, Member me) {
        Member reviewer = a.getTask().getReviewer();
        return reviewer != null && reviewer.getId().equals(me.getId());
    }
}
