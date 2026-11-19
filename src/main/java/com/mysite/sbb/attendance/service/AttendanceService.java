package com.mysite.sbb.attendance.service;

import com.mysite.sbb.attendance.constant.LeaveStatus;
import com.mysite.sbb.attendance.constant.LeaveType;
import com.mysite.sbb.attendance.dto.MonthCalendar;
import com.mysite.sbb.attendance.dto.WeekRow;
import com.mysite.sbb.attendance.entity.Attendance;
import com.mysite.sbb.attendance.entity.LeaveRequest;
import com.mysite.sbb.attendance.entity.WorkSchedule;
import com.mysite.sbb.attendance.repository.AttendanceRepository;
import com.mysite.sbb.attendance.repository.LeaveRequestRepository;
import com.mysite.sbb.attendance.repository.WorkScheduleRepository;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.outbox.service.OutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

// 근태 : 출퇴근, 근무 스케줄, 휴가
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final MemberService memberService;
    private final OutboxService outboxService;

    // ---------- 출퇴근 ----------

    @Transactional(readOnly = true)
    public Attendance getToday(Member me) {
        return attendanceRepository.findByMemberIdAndWorkDate(me.getId(), LocalDate.now()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Attendance> getRecent(Member me, int days) {
        LocalDate today = LocalDate.now();
        return attendanceRepository.findByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(me.getId(), today.minusDays(days), today);
    }

    @Transactional(readOnly = true)
    public List<Attendance> getByDate(LocalDate date) {
        return attendanceRepository.findByWorkDateOrderByClockInAsc(date);
    }

    // [신뢰성] 일시적인 DB 오류(커넥션 끊김, 락 타임아웃 등)는 @Retryable 이 100ms → 200ms → 400ms 간격으로 다시 시도
    //  하루 한 줄(unique) 이라서 재시도해도 두 번 출근 처리되지 않는다
    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public Attendance clockIn(String username) {
        Member me = memberService.getMember(username);
        LocalDate today = LocalDate.now();
        if (attendanceRepository.findByMemberIdAndWorkDate(me.getId(), today).isPresent()) {
            throw new BusinessException("오늘은 이미 출근했어요.");
        }
        return attendanceRepository.save(Attendance.builder().member(me).workDate(today).clockIn(LocalDateTime.now()).build());
    }

    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public Attendance clockOut(String username) {
        Member me = memberService.getMember(username);
        Attendance a = attendanceRepository.findByMemberIdAndWorkDate(me.getId(), LocalDate.now())
                .orElseThrow(() -> new BusinessException("먼저 출근을 눌러 주세요."));
        a.setClockOut(LocalDateTime.now());       // 다시 누르면 퇴근 시각을 고친다
        return a;
    }

    // ---------- 스케줄 ----------

    public static LocalDate weekStart(LocalDate any) {
        return (any == null ? LocalDate.now() : any).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public List<WorkSchedule> getMyWeek(Member me, LocalDate monday) {
        return workScheduleRepository.findByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(me.getId(), monday, monday.plusDays(6));
    }

    // 관리자 : 직원 × 요일 표
    @Transactional(readOnly = true)
    public List<WeekRow> getWeekTable(LocalDate monday) {
        List<WorkSchedule> all = workScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(monday, monday.plusDays(6));
        List<WeekRow> rows = new ArrayList<>();
        for (Member m : memberService.getActiveMembers()) {
            List<List<WorkSchedule>> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate d = monday.plusDays(i);
                days.add(all.stream().filter(s -> s.getMember().getId().equals(m.getId()) && s.getWorkDate().equals(d)).toList());
            }
            rows.add(new WeekRow(m, days));
        }
        return rows;
    }

    // 월별 달력 (memberId 가 있으면 그 직원 근무만)
    @Transactional(readOnly = true)
    public MonthCalendar getMonthCalendar(YearMonth month, Long memberId) {
        LocalDate first = weekStart(month.atDay(1));
        LocalDate last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<WorkSchedule> all = memberId == null
                ? workScheduleRepository.findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(first, last)
                : workScheduleRepository.findByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(memberId, first, last);
        LocalDate today = LocalDate.now();
        List<List<MonthCalendar.Day>> weeks = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(7)) {
            List<MonthCalendar.Day> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate day = d.plusDays(i);
                week.add(new MonthCalendar.Day(day, YearMonth.from(day).equals(month), day.equals(today),
                        all.stream().filter(s -> s.getWorkDate().equals(day)).toList()));
            }
            weeks.add(week);
        }
        int total = (int) all.stream().filter(s -> YearMonth.from(s.getWorkDate()).equals(month)).count();
        return new MonthCalendar(month, weeks, total);
    }

    public WorkSchedule addSchedule(Long memberId, LocalDate date, LocalTime start, LocalTime end, String memo, String adminUsername) {
        if (!end.isAfter(start)) throw new BusinessException("끝나는 시각이 시작 시각보다 늦어야 해요.");
        Member member = memberService.getMember(memberId);
        WorkSchedule s = workScheduleRepository.save(WorkSchedule.builder()
                .member(member).workDate(date).startTime(start).endTime(end)
                .memo(MemberService.emptyToNull(memo)).createdBy(memberService.getMember(adminUsername)).build());
        outboxService.notify(List.of(member), NotificationType.ATTENDANCE, "근무 스케줄 배정",
                date + " " + start + " ~ " + end + (s.getMemo() == null ? "" : " (" + s.getMemo() + ")"),
                "/schedule?week=" + weekStart(date), "WorkSchedule", s.getId(), null);
        return s;
    }

    public void deleteSchedule(Long id) {
        workScheduleRepository.deleteById(id);
    }

    // ---------- 휴가 ----------

    @Transactional(readOnly = true)
    public List<LeaveRequest> getMyLeaves(Member me) {
        return leaveRequestRepository.findByMemberIdOrderByIdDesc(me.getId());
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingLeaves() {
        return leaveRequestRepository.findByStatusOrderByStartDateAsc(LeaveStatus.REQUESTED);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getRecentLeaves() {
        return leaveRequestRepository.findTop50ByOrderByIdDesc();
    }

    public LeaveRequest requestLeave(String username, LeaveType type, LocalDate start, LocalDate end, String reason) {
        if (end.isBefore(start)) throw new BusinessException("끝나는 날이 시작일보다 빨라요.");
        Member me = memberService.getMember(username);
        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
                .member(me).type(type).startDate(start).endDate(end).reason(MemberService.emptyToNull(reason))
                .status(LeaveStatus.REQUESTED).created(LocalDateTime.now()).build());
        outboxService.notify(memberService.getAdmins(), NotificationType.ATTENDANCE, "휴가 신청 : " + me.getDisplayName(),
                type.getLabel() + " " + start + " ~ " + end, "/admin/leave", "LeaveRequest", leave.getId(), null);
        return leave;
    }

    public void cancelLeave(Long id, String username) {
        LeaveRequest leave = getLeave(id);
        if (!leave.getMember().getUsername().equals(username)) throw new BusinessException("내 휴가만 취소할 수 있어요.");
        if (leave.getStatus() != LeaveStatus.REQUESTED) throw new BusinessException("승인 대기 중인 휴가만 취소할 수 있어요.");
        leave.setStatus(LeaveStatus.CANCELED);
    }

    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public void decideLeave(Long id, boolean approve, String comment, String adminUsername) {
        LeaveRequest leave = getLeave(id);
        if (leave.getStatus() != LeaveStatus.REQUESTED) throw new BusinessException("이미 처리된 휴가예요.");
        leave.setStatus(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        leave.setDecidedBy(memberService.getMember(adminUsername));
        leave.setDecidedAt(LocalDateTime.now());
        leave.setDecisionComment(MemberService.emptyToNull(comment));
        outboxService.notify(List.of(leave.getMember()), NotificationType.ATTENDANCE,
                "휴가 " + (approve ? "승인" : "반려") + " : " + leave.getType().getLabel(),
                leave.getStartDate() + " ~ " + leave.getEndDate() + (comment == null || comment.isBlank() ? "" : " · " + comment),
                "/leave", "LeaveRequest", leave.getId(), "leave-decided:" + leave.getId());
    }

    private LeaveRequest getLeave(Long id) {
        return leaveRequestRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("휴가 신청을 찾을 수 없어요."));
    }
}
