package com.mysite.sbb.admin.service;

import com.mysite.sbb.admin.dto.AdminDashboard;
import com.mysite.sbb.attendance.service.AttendanceService;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import com.mysite.sbb.report.constant.ReportStatus;
import com.mysite.sbb.report.repository.ReportRepository;
import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.entity.TodoAssignment;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 관리자 대시보드 집계 (읽기 전용 — 상태를 바꾸는 업무 로직은 기존 서비스에 그대로 있다)
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final TodoAssignmentRepository todoAssignmentRepository;
    private final ReportRepository reportRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AttendanceService attendanceService;

    @Transactional(readOnly = true)
    public AdminDashboard build(LocalDate today) {
        LocalDate monday = AttendanceService.weekStart(today);
        LocalDate sunday = monday.plusDays(6);
        List<TodoAssignment> week = todoAssignmentRepository.findByWorkDateBetween(monday, sunday);
        List<TodoAssignment> todays = week.stream().filter(a -> a.getWorkDate().equals(today)).toList();

        long total = todays.size();
        long done = todays.stream().filter(a -> a.getStatus().isDone()).count();
        int rate = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);

        // 직원 기준 : 오늘 몫을 모두 낸 직원 수
        Map<Long, List<TodoAssignment>> byMember = todays.stream()
                .collect(Collectors.groupingBy(a -> a.getMember().getId(), LinkedHashMap::new, Collectors.toList()));
        long membersDone = byMember.values().stream()
                .filter(list -> list.stream().allMatch(a -> a.getStatus().isDone())).count();

        // 근무지별 (근무지 없는 직원은 '근무지 미지정')
        Map<String, List<TodoAssignment>> byWorkplace = todays.stream()
                .collect(Collectors.groupingBy(a -> a.getMember().getWorkplace() != null ? a.getMember().getWorkplace().getName() : "근무지 미지정",
                        LinkedHashMap::new, Collectors.toList()));
        List<AdminDashboard.WorkplaceRow> rows = new ArrayList<>();
        byWorkplace.forEach((name, list) -> rows.add(new AdminDashboard.WorkplaceRow(name,
                list.size(),
                list.stream().filter(a -> a.getStatus().isDone()).count(),
                list.stream().filter(a -> a.getStatus() == AssignmentStatus.PENDING_REVIEW).count(),
                list.stream().filter(a -> a.getStatus() == AssignmentStatus.REWORK).count(),
                list.stream().filter(a -> a.getStatus() == AssignmentStatus.APPROVED).count())));

        // 이번 주 제출 (그 날짜 몫 중 제출된 수)
        List<AdminDashboard.DayBar> bars = new ArrayList<>();
        long max = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            long c = week.stream().filter(a -> a.getWorkDate().equals(d) && a.getStatus().isDone()).count();
            max = Math.max(max, c);
            bars.add(new AdminDashboard.DayBar(d, c, d.equals(today)));
        }

        long reviewWaiting = todoAssignmentRepository.findByStatusOrderByWorkDateDescIdDesc(AssignmentStatus.PENDING_REVIEW).size();
        long approvalWaiting = reportRepository.countByStatusIn(List.of(ReportStatus.SUBMITTED, ReportStatus.IN_REVIEW));

        return new AdminDashboard(today, total, done, rate, byMember.size(), membersDone,
                reviewWaiting, approvalWaiting, rows, bars, max,
                outboxEventRepository.countByStatus(OutboxStatus.DEAD),
                attendanceService.getPendingLeaves().size());
    }

    @Transactional(readOnly = true)
    public long outboxDoneToday(LocalDate today) {
        return outboxEventRepository.countByStatusAndProcessedAtGreaterThanEqual(OutboxStatus.DONE, today.atStartOfDay());
    }
}
