package com.mysite.sbb.common.advice;

import com.mysite.sbb.admin.service.AdminDashboardService;
import com.mysite.sbb.attendance.service.AttendanceService;
import com.mysite.sbb.batch.service.JobExecutionService;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.service.OutboxService;
import com.mysite.sbb.report.service.ReportService;
import com.mysite.sbb.todo.service.TodoAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 사이드바·상단바에서 쓰는 값 (모든 화면 공통)
//  navInbox : 결재함 배지 / navReviewWaiting : 검수 대기 수
//  navDead, navPendingEvents, navFailedJobs, navNextBatch : 관리자 상태 카드 (스케줄러 · 다음 자동 배정 · 대기 이벤트)
//  navLeavePending : 휴가 승인 대기 / navBoards : 게시판 탭 (/board/** 에서만) / outboxDoneToday : 재처리 모니터
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutModelAdvice {

    private final MemberRepository memberRepository;
    private final ReportService reportService;
    private final TodoAssignmentService todoAssignmentService;
    private final OutboxService outboxService;
    private final JobExecutionService jobExecutionService;
    private final AttendanceService attendanceService;
    private final BoardService boardService;
    private final AdminDashboardService adminDashboardService;

    @Value("${app.batch.generate-cron:0 5 0 * * *}")
    private String generateCron;

    @Value("${app.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    @ModelAttribute
    public void layout(HttpServletRequest request, Principal principal, Model model) {
        // 화면을 그리는 GET 요청에서만 (POST 는 처리 후 redirect 하므로 계산할 필요가 없다)
        if (!"GET".equals(request.getMethod()) || principal == null) {
            return;
        }
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        Member me = memberRepository.findByUsername(principal.getName()).orElse(null);
        if (me == null) {
            return;
        }
        model.addAttribute("navInbox", reportService.getInbox(me).size());
        model.addAttribute("navReviewWaiting", me.isAdmin() ? todoAssignmentService.getReviewQueue(me).size()
                : todoAssignmentService.countReviewWaiting(me));
        if (me.isAdmin()) {
            model.addAttribute("navDead", outboxService.count(OutboxStatus.DEAD));
            model.addAttribute("navPendingEvents", outboxService.count(OutboxStatus.PENDING));
            model.addAttribute("navFailedJobs", jobExecutionService.getFailed().size());
            model.addAttribute("navLeavePending", attendanceService.getPendingLeaves().size());
            model.addAttribute("navSchedulingEnabled", schedulingEnabled);
            model.addAttribute("navNextBatch", nextBatch());
            if (uri != null && uri.startsWith("/admin/monitor")) {
                model.addAttribute("outboxDoneToday", adminDashboardService.outboxDoneToday(LocalDate.now()));
            }
        }
        if (uri != null && uri.startsWith("/board")) {
            model.addAttribute("navBoards", boardService.getBoards(me));
        }
    }

    private String nextBatch() {
        try {
            LocalDateTime next = CronExpression.parse(generateCron).next(LocalDateTime.now());
            return next == null ? "-" : next.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (IllegalArgumentException e) {
            return "-";
        }
    }
}
