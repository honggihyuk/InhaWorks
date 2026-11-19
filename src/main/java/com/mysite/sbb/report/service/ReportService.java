package com.mysite.sbb.report.service;

import com.mysite.sbb.common.dto.AnswerInput;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.outbox.service.OutboxService;
import com.mysite.sbb.report.constant.ApprovalDecision;
import com.mysite.sbb.report.constant.ReportFieldType;
import com.mysite.sbb.report.constant.ReportStatus;
import com.mysite.sbb.report.entity.*;
import com.mysite.sbb.report.repository.ReportApprovalRepository;
import com.mysite.sbb.report.repository.ReportCommentRepository;
import com.mysite.sbb.report.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

// 보고서 : 작성·제출, 결재(단계별 승인 / 반려), 댓글, 결재함
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportApprovalRepository reportApprovalRepository;
    private final ReportCommentRepository reportCommentRepository;
    private final ReportFormService reportFormService;
    private final MemberService memberService;
    private final FileStore fileStore;
    private final OutboxService outboxService;

    @Transactional(readOnly = true)
    public List<Report> getMine(Member me) {
        return reportRepository.findTop50ByAuthorIdOrderByIdDesc(me.getId());
    }

    @Transactional(readOnly = true)
    public List<Report> getByForm(Long formId) {
        return reportRepository.findByFormIdOrderByIdDesc(formId);
    }

    // 작성자, 결재선에 있는 사람, 관리자만 볼 수 있다
    @Transactional(readOnly = true)
    public Report getForView(Long id, Member me) {
        Report r = get(id);
        boolean inLine = r.getApprovals().stream().anyMatch(a -> a.getApprover().getId().equals(me.getId()));
        if (!r.getAuthor().getId().equals(me.getId()) && !inLine && !me.isAdmin()) {
            throw new BusinessException("이 보고서를 볼 수 있는 권한이 없어요.");
        }
        return r;
    }

    public boolean canDecide(Report r, Member me) {
        ReportApproval cur = r.getCurrentApproval();
        return cur != null && cur.getApprover().getId().equals(me.getId());
    }

    @Transactional(readOnly = true)
    public List<ReportApproval> getInbox(Member me) {
        return reportApprovalRepository.findInbox(me.getId(), ApprovalDecision.WAITING).stream()
                .filter(ap -> ap.getReport().getStatus().isOpen()).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportApproval> getDecided(Member me) {
        return reportApprovalRepository.findTop30ByApproverIdAndDecisionNotOrderByDecidedAtDesc(me.getId(), ApprovalDecision.WAITING);
    }

    @Transactional(readOnly = true)
    public List<ReportComment> getComments(Long reportId) {
        return reportCommentRepository.findByReportIdOrderByIdAsc(reportId);
    }

    // ---------- 작성 · 제출 ----------

    // [신뢰성] 중복 제출 방지 : submitToken(화면을 열 때 만든 UUID) 으로 이미 만든 보고서가 있으면 그걸 돌려준다.
    //  동시에 두 요청이 들어오면 submitToken unique 제약이 두 번째 INSERT 를 막는다 (컨트롤러가 안내).
    // [신뢰성] 이미지·문서·서명 파일은 트랜잭션이 롤백되면 FileStore 가 지운다 (보상)
    public Report submit(Long formId, String username, Map<Long, AnswerInput> inputs, String submitToken) {
        if (submitToken != null) {
            Optional<Report> dup = reportRepository.findBySubmitToken(submitToken);
            if (dup.isPresent()) return dup.get();
        }
        Member me = memberService.getMember(username);
        ReportForm form = reportFormService.get(formId);
        if (!form.canWrite(me)) {
            throw new BusinessException("이 보고서를 쓸 수 있는 대상이 아니에요.");
        }
        Report report = Report.builder()
                .form(form)
                .author(me)
                .status(ReportStatus.SUBMITTED)
                .currentStep(form.getApprovers().isEmpty() ? 0 : 1)
                .submittedAt(LocalDateTime.now())
                .submitToken(submitToken)
                .build();

        for (ReportField field : form.getActiveFields()) {
            AnswerInput in = inputs.getOrDefault(field.getId(), new AnswerInput(List.of(), null, null));
            String value = in.joinedValue();
            if (field.isRequired()) {
                boolean ok = switch (field.getType()) {
                    case IMAGE, FILE -> in.hasFile();
                    case SIGNATURE -> in.hasSignature();
                    default -> value != null;
                };
                if (!ok) throw new BusinessException("필수 항목을 채워 주세요 : " + field.getLabel());
            }
            if (field.getType() == ReportFieldType.CHOICE && value != null && !field.getOptionList().contains(value)) {
                throw new BusinessException("선택지에 없는 값이에요 : " + field.getLabel());
            }
            ReportAnswer answer = ReportAnswer.builder().report(report).field(field).build();
            switch (field.getType()) {
                case IMAGE -> answer.setFile(fileStore.storeImage(in.file()));
                case FILE -> answer.setFile(fileStore.storeAttachment(in.file()));
                case SIGNATURE -> answer.setFile(fileStore.storeSignature(in.signature()));
                default -> answer.setValue(value);
            }
            report.getAnswers().add(answer);
        }

        // 양식의 결재선을 이 보고서용으로 복사
        for (ReportApprover ap : form.getApprovers()) {
            report.getApprovals().add(ReportApproval.builder()
                    .report(report).step(ap.getStep()).approver(ap.getApprover()).decision(ApprovalDecision.WAITING).build());
        }
        reportRepository.save(report);

        // 1단계 결재자에게 알림 (같은 트랜잭션에 Outbox 저장)
        ReportApproval first = report.getCurrentApproval();
        if (first != null) {
            outboxService.notify(List.of(first.getApprover()), NotificationType.APPROVAL,
                    "결재 요청 : " + form.getTitle(), me.getDisplayName() + " 님의 보고서 (1단계)",
                    "/report/" + report.getId(), "Report", report.getId(), "report-step:" + report.getId() + ":1");
        }
        return report;
    }

    // ---------- 결재 ----------

    // [신뢰성] 결재자 두 명이 동시에 처리하면(@Version 충돌) @Retryable 이 새 트랜잭션으로 다시 시도 →
    //  최신 상태를 읽고 "결재 차례가 아니에요" 로 안내한다. 일시적인 DB 연결 오류도 같은 방식으로 재시도.
    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public Report decide(Long reportId, String username, boolean approve, String comment) {
        Member me = memberService.getMember(username);
        Report r = get(reportId);
        ReportApproval cur = r.getCurrentApproval();
        if (cur == null || !cur.getApprover().getId().equals(me.getId())) {
            throw new BusinessException("지금은 결재 차례가 아니에요. (" + r.getStatus().getLabel() + ")");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw new BusinessException("반려 사유를 입력해 주세요.");
        }
        cur.setDecision(approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        cur.setComment(MemberService.emptyToNull(comment));
        cur.setDecidedAt(LocalDateTime.now());

        String title = r.getForm().getTitle();
        String link = "/report/" + r.getId();
        int lastStep = r.getApprovals().size();
        if (!approve) {
            r.setStatus(ReportStatus.REJECTED);
            r.setCompletedAt(LocalDateTime.now());
            outboxService.notify(List.of(r.getAuthor()), NotificationType.REPORT, "보고서 반려 : " + title,
                    me.getDisplayName() + " 님이 반려했어요. 사유 : " + comment.strip(), link, "Report", r.getId(),
                    "report-rejected:" + r.getId());
        } else if (cur.getStep() >= lastStep) {
            r.setStatus(ReportStatus.APPROVED);
            r.setCompletedAt(LocalDateTime.now());
            outboxService.notify(List.of(r.getAuthor()), NotificationType.REPORT, "검토완료 : " + title,
                    "모든 결재가 끝났어요.", link, "Report", r.getId(), "report-done:" + r.getId());
        } else {
            r.setStatus(ReportStatus.IN_REVIEW);
            r.setCurrentStep(cur.getStep() + 1);
            ReportApproval next = r.getCurrentApproval();
            outboxService.notify(List.of(next.getApprover()), NotificationType.APPROVAL, "결재 요청 : " + title,
                    r.getAuthor().getDisplayName() + " 님의 보고서 (" + next.getStep() + "단계)", link, "Report", r.getId(),
                    "report-step:" + r.getId() + ":" + next.getStep());
            outboxService.notify(List.of(r.getAuthor()), NotificationType.REPORT, "검토중 : " + title,
                    cur.getStep() + "단계 " + me.getDisplayName() + " 님이 승인했어요.", link, "Report", r.getId(),
                    "report-approved:" + r.getId() + ":" + cur.getStep());
        }
        return r;
    }

    // ---------- 승인 실행 취소 ----------
    //  내가 승인한 지 UNDO_WINDOW 안이고, 다음 단계 결재자가 아직 처리하지 않았을 때만 되돌린다
    //  → 내 단계가 다시 '대기', 보고서는 내 차례로 돌아온다 (마지막 단계였다면 검토완료도 풀린다)
    public static final Duration UNDO_WINDOW = Duration.ofMinutes(10);

    public ReportApproval undoableApproval(Report r, Member me) {
        ReportApproval mine = r.getApprovals().stream()
                .filter(ap -> ap.getApprover().getId().equals(me.getId()) && ap.getDecision() == ApprovalDecision.APPROVED)
                .max(Comparator.comparingInt(ReportApproval::getStep)).orElse(null);
        if (mine == null || mine.getDecidedAt() == null
                || !mine.getDecidedAt().plus(UNDO_WINDOW).isAfter(LocalDateTime.now())) {
            return null;
        }
        int lastStep = r.getApprovals().size();
        boolean finalApproved = r.getStatus() == ReportStatus.APPROVED && mine.getStep() == lastStep;
        boolean nextWaiting = r.getStatus().isOpen() && r.getCurrentStep() == mine.getStep() + 1
                && r.getCurrentApproval() != null;
        return (finalApproved || nextWaiting) ? mine : null;
    }

    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public Report undoApprove(Long reportId, String username) {
        Member me = memberService.getMember(username);
        Report r = get(reportId);
        ReportApproval mine = undoableApproval(r, me);
        if (mine == null) {
            throw new BusinessException("되돌릴 수 없어요. 승인 후 " + UNDO_WINDOW.toMinutes()
                    + "분 안에, 다음 결재자가 처리하기 전까지만 취소할 수 있어요.");
        }
        ReportApproval next = r.getCurrentApproval();
        mine.setDecision(ApprovalDecision.WAITING);
        mine.setComment(null);
        mine.setDecidedAt(null);
        r.setCurrentStep(mine.getStep());
        r.setStatus(mine.getStep() == 1 ? ReportStatus.SUBMITTED : ReportStatus.IN_REVIEW);
        r.setCompletedAt(null);

        String title = r.getForm().getTitle();
        String link = "/report/" + r.getId();
        outboxService.notify(List.of(r.getAuthor()), NotificationType.REPORT, "승인 취소 : " + title,
                mine.getStep() + "단계 " + me.getDisplayName() + " 님이 승인을 취소했어요. 다시 검토 중이에요.",
                link, "Report", r.getId(), null);
        if (next != null) {
            outboxService.notify(List.of(next.getApprover()), NotificationType.APPROVAL, "결재 요청 취소 : " + title,
                    "앞 단계 승인이 취소되어 지금은 결재할 차례가 아니에요.", link, "Report", r.getId(), null);
        }
        return r;
    }

    // ---------- 댓글 ----------

    public void addComment(Long id, String username, String content) {
        if (content == null || content.isBlank()) return;
        Member me = memberService.getMember(username);
        Report r = getForView(id, me);
        reportCommentRepository.save(ReportComment.builder()
                .report(r).author(me).content(content.strip()).created(LocalDateTime.now()).build());

        // 작성자 + 결재선 + @멘션 에게 알림 (나는 빼고)
        Set<Member> targets = new LinkedHashSet<>();
        targets.add(r.getAuthor());
        r.getApprovals().forEach(a -> targets.add(a.getApprover()));
        targets.addAll(memberService.findMentioned(content));
        targets.removeIf(m -> m.getId().equals(me.getId()));
        outboxService.notify(targets, NotificationType.REPORT, "새 댓글 : " + r.getForm().getTitle(),
                me.getDisplayName() + " : " + content.strip(), "/report/" + r.getId(), "Report", r.getId(), null);
    }

    private Report get(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("보고서를 찾을 수 없어요."));
    }
}
