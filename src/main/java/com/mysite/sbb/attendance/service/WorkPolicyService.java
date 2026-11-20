package com.mysite.sbb.attendance.service;

import com.mysite.sbb.hr.constant.HistoryField;
import com.mysite.sbb.hr.service.HrHistoryService;
import com.mysite.sbb.attendance.constant.Tone;
import com.mysite.sbb.attendance.constant.WorkPolicyType;
import com.mysite.sbb.attendance.dto.PolicyTab;
import com.mysite.sbb.attendance.dto.WorkPolicyForm;
import com.mysite.sbb.attendance.entity.ShiftType;
import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.attendance.entity.WorkSchedule;
import com.mysite.sbb.attendance.repository.ShiftTypeRepository;
import com.mysite.sbb.attendance.repository.WorkPolicyRepository;
import com.mysite.sbb.attendance.repository.WorkScheduleRepository;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

// 근무 정책(근무제)과 근무 유형 : 만들기·고치기·지우기, 직원 배정, 처음 실행할 때 기본 정책 만들기
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WorkPolicyService {

    public static final int WEEKLY_LIMIT_MINUTES = 52 * 60;     // 주 최대 근로시간 (소정 40 + 연장 12)

    private final WorkPolicyRepository workPolicyRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final OrganizationService organizationService;
    private final HrHistoryService historyService;

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public List<WorkPolicy> getPolicies() {
        return workPolicyRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public WorkPolicy getPolicy(Long id) {
        return workPolicyRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("근무 정책을 찾을 수 없어요."));
    }

    @Transactional(readOnly = true)
    public ShiftType getShiftType(Long id) {
        return shiftTypeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("근무 유형을 찾을 수 없어요."));
    }

    // 근무제 탭 : 정책마다 소속 직원(재직 중). 정책이 없는 직원은 탭에 들어가지 않는다
    @Transactional(readOnly = true)
    public List<PolicyTab> getTabs() {
        Map<Long, List<Member>> byPolicy = memberService.getActiveMembers().stream()
                .filter(m -> m.getWorkPolicy() != null)
                .collect(Collectors.groupingBy(m -> m.getWorkPolicy().getId(), LinkedHashMap::new, Collectors.toList()));
        return getPolicies().stream().map(p -> new PolicyTab(p, byPolicy.getOrDefault(p.getId(), List.of()))).toList();
    }

    // ---------- 정책 만들기 / 고치기 / 지우기 ----------

    public WorkPolicy create(WorkPolicyForm form) {
        WorkPolicy p = WorkPolicy.builder().created(LocalDateTime.now()).sortOrder((int) workPolicyRepository.count()).build();
        copy(form, p);
        return workPolicyRepository.save(p);
    }

    public void update(Long id, WorkPolicyForm form) {
        copy(form, getPolicy(id));
    }

    private void copy(WorkPolicyForm f, WorkPolicy p) {
        String name = MemberService.emptyToNull(f.getName());
        if (name == null) throw new BusinessException("근무 정책 이름을 적어 주세요.");
        if (f.getType() == null) throw new BusinessException("근무제 종류를 골라 주세요.");
        if (f.getWeeklyHours() < 1 || f.getWeeklyHours() > 52) throw new BusinessException("1주 소정근로시간은 1~52시간이에요.");
        if (f.getBreakMinutes() < 0 || f.getBreakMinutes() > 240) throw new BusinessException("휴게시간은 0~240분이에요.");
        boolean hasTime = f.getStartTime() != null && f.getEndTime() != null;
        if (hasTime && !f.getEndTime().isAfter(f.getStartTime())) throw new BusinessException("퇴근 시각이 출근 시각보다 늦어야 해요.");
        p.setName(name.length() > 40 ? name.substring(0, 40) : name);
        p.setType(f.getType());
        p.setWeeklyHours(f.getWeeklyHours());
        List<DayOfWeek> days = f.getWorkDays() == null ? List.of() : f.getWorkDays().stream().distinct().sorted().toList();
        p.setWorkDays(days.stream().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(",")));   // MON,TUE,...
        p.setStartTime(hasTime ? f.getStartTime() : null);
        p.setEndTime(hasTime ? f.getEndTime() : null);
        p.setBreakMinutes(f.getBreakMinutes());
        p.setWorkplace(organizationService.getWorkplace(f.getWorkplaceId()));
        p.setTone(f.getTone());
    }

    // 지우기 : 소속 직원이 있으면 막는다. 이 정책의 근무 유형으로 배정된 지난 스케줄은 시간·이름을 남기고 유형 연결만 끊는다
    public String delete(Long id) {
        WorkPolicy p = getPolicy(id);
        long members = memberRepository.findAll().stream()
                .filter(m -> m.getWorkPolicy() != null && m.getWorkPolicy().getId().equals(id)).count();
        if (members > 0) throw new BusinessException("소속 직원 " + members + "명을 다른 근무제로 옮긴 뒤에 지울 수 있어요.");
        for (ShiftType s : p.getShiftTypes()) detachSchedules(s);
        workPolicyRepository.delete(p);
        return p.getName();
    }

    // ---------- 근무 유형 ----------

    public ShiftType addShiftType(Long policyId, String name, LocalTime start, LocalTime end, boolean off, Tone tone) {
        WorkPolicy p = getPolicy(policyId);
        ShiftType s = ShiftType.builder().build();
        fill(s, name, start, end, off, tone);
        p.addShiftType(s);
        return shiftTypeRepository.save(s);
    }

    public void updateShiftType(Long policyId, Long shiftId, String name, LocalTime start, LocalTime end, boolean off, Tone tone) {
        ShiftType s = getShiftType(shiftId);
        if (!s.getPolicy().getId().equals(policyId)) throw new BusinessException("이 정책의 근무 유형이 아니에요.");
        fill(s, name, start, end, off, tone);
    }

    public String deleteShiftType(Long policyId, Long shiftId) {
        ShiftType s = getShiftType(shiftId);
        WorkPolicy p = s.getPolicy();
        if (!p.getId().equals(policyId)) throw new BusinessException("이 정책의 근무 유형이 아니에요.");
        detachSchedules(s);
        p.getShiftTypes().remove(s);
        return s.getName();
    }

    private void fill(ShiftType s, String name, LocalTime start, LocalTime end, boolean off, Tone tone) {
        String n = MemberService.emptyToNull(name);
        if (n == null) throw new BusinessException("근무 유형 이름을 적어 주세요.");
        if (!off) {
            if (start == null || end == null) throw new BusinessException("근무 유형의 시작·끝 시각을 적어 주세요.");
            if (!end.isAfter(start)) throw new BusinessException("끝나는 시각이 시작 시각보다 늦어야 해요.");
        }
        s.setName(n.length() > 30 ? n.substring(0, 30) : n);
        s.setOff(off);
        s.setStartTime(off ? null : start);
        s.setEndTime(off ? null : end);
        s.setTone(tone);
    }

    private void detachSchedules(ShiftType s) {
        for (WorkSchedule ws : workScheduleRepository.findByShiftTypeId(s.getId())) {
            if (ws.getMemo() == null) ws.setMemo(s.getName());
            ws.setShiftType(null);
        }
    }

    // ---------- 직원 배정 ----------

    // 고른 직원들을 이 정책으로 옮긴다
    public int assignMembers(Long policyId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) throw new BusinessException("옮길 직원을 골라 주세요.");
        WorkPolicy p = getPolicy(policyId);
        int n = 0;
        for (Long id : new LinkedHashSet<>(memberIds)) {
            Member m = memberService.getMember(id);
            if (m.getWorkPolicy() == null || !m.getWorkPolicy().getId().equals(policyId)) {
                historyService.record(m, HistoryField.WORK_POLICY, m.getWorkPolicy() == null ? null : m.getWorkPolicy().getName(), p.getName());
                m.setWorkPolicy(p);
                n++;
            }
        }
        return n;
    }

    public WorkPolicy findOrNull(Long id) {
        return id == null ? null : workPolicyRepository.findById(id).orElse(null);
    }

    // ---------- 처음 실행 : 기본 정책 ----------

    // 정책이 하나도 없으면 기본 정책 두 개를 만들고, 정책이 없는 직원에게 배정한다 (기존 데이터와 호환)
    //  - 고정 근무제 (사무실) : 월~금 09:00~18:00, 휴게 1시간 / 근무 유형 : 사무실 근무, 오전 근무, 휴무
    //  - 스케줄 근무제 (매장) : 주 40시간 / 근무 유형 : 오픈, 미들, 마감, 휴무
    //  demo = true(예시 데이터를 막 만든 경우) 이면 관리자가 아닌 직원을 매장 스케줄 근무제로 배정한다
    //  이미 있는 스케줄은 메모가 근무 유형 이름과 같으면 그 유형으로 연결한다
    public void ensureDefaults(boolean demo) {
        if (workPolicyRepository.count() > 0) {
            assignUnassigned(null, null, false);
            return;
        }
        log.info("=======> 기본 근무 정책 만들기 (고정 근무제 · 스케줄 근무제)");
        WorkPolicy office = WorkPolicy.builder().name("사무실 고정 근무").type(WorkPolicyType.FIXED).weeklyHours(40)
                .workDays("MON,TUE,WED,THU,FRI").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .breakMinutes(60).sortOrder(0).created(LocalDateTime.now()).build();
        office.addShiftType(shift("사무실 근무", 9, 18, Tone.BLUE));
        office.addShiftType(shift("오전 근무", 9, 13, Tone.TEAL));
        office.addShiftType(offShift());
        workPolicyRepository.save(office);

        WorkPolicy store = WorkPolicy.builder().name("매장 스케줄 근무").type(WorkPolicyType.SHIFT).weeklyHours(40)
                .workDays("MON,TUE,WED,THU,FRI,SAT,SUN").breakMinutes(60).sortOrder(1).created(LocalDateTime.now()).build();
        store.addShiftType(shift("오픈", 6, 12, Tone.YELLOW));
        store.addShiftType(shift("미들", 11, 16, Tone.GREEN));
        store.addShiftType(shift("마감", 17, 22, Tone.BLUE));
        store.addShiftType(offShift());
        workPolicyRepository.save(store);

        assignUnassigned(office, store, demo);
        linkSchedulesByMemo();
    }

    private void assignUnassigned(WorkPolicy office, WorkPolicy store, boolean demo) {
        WorkPolicy fallback = office != null ? office : getPolicies().get(0);
        for (Member m : memberRepository.findAll()) {
            if (m.getWorkPolicy() != null) continue;
            m.setWorkPolicy(demo && store != null && m.getRole() != MemberRole.ADMIN ? store : fallback);
        }
    }

    private void linkSchedulesByMemo() {
        for (WorkSchedule ws : workScheduleRepository.findByShiftTypeIsNull()) {
            WorkPolicy p = ws.getMember().getWorkPolicy();
            if (p == null || ws.getMemo() == null) continue;
            p.getShiftTypes().stream().filter(s -> s.getName().equals(ws.getMemo().strip())).findFirst()
                    .ifPresent(ws::setShiftType);
        }
    }

    private static ShiftType shift(String name, int from, int to, Tone tone) {
        return ShiftType.builder().name(name).startTime(LocalTime.of(from, 0)).endTime(LocalTime.of(to, 0)).tone(tone).build();
    }

    private static ShiftType offShift() {
        return ShiftType.builder().name("휴무").off(true).tone(Tone.GREY).build();
    }
}
