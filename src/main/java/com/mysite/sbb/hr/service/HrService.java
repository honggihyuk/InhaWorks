package com.mysite.sbb.hr.service;

import com.mysite.sbb.attendance.constant.LeaveStatus;
import com.mysite.sbb.attendance.entity.Attendance;
import com.mysite.sbb.attendance.entity.LeaveRequest;
import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.attendance.entity.WorkSchedule;
import com.mysite.sbb.attendance.repository.AttendanceRepository;
import com.mysite.sbb.attendance.repository.LeaveRequestRepository;
import com.mysite.sbb.attendance.repository.WorkScheduleRepository;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.hr.constant.DocScope;
import com.mysite.sbb.hr.constant.HistoryField;
import com.mysite.sbb.hr.dto.HrViews.AttendanceMonth;
import com.mysite.sbb.hr.dto.HrViews.Day;
import com.mysite.sbb.hr.dto.HrViews.DocRow;
import com.mysite.sbb.hr.dto.HrViews.HrAlert;
import com.mysite.sbb.hr.entity.HrDocType;
import com.mysite.sbb.hr.entity.HrDocument;
import com.mysite.sbb.hr.repository.HrDocTypeRepository;
import com.mysite.sbb.hr.repository.HrDocumentRepository;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

// 인사 : 서류 종류·직원 서류(비공개 파일 + 열람 권한), 미제출 필수 서류, 계약 만료·복직 알림, 직원 허브의 근태 요약
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HrService {

    public static final int ALERT_DAYS = 30;

    private final HrDocTypeRepository docTypeRepository;
    private final HrDocumentRepository documentRepository;
    private final HrHistoryService historyService;
    private final MemberService memberService;
    private final FileStore fileStore;
    private final AttendanceRepository attendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    // ---------- 서류 종류 ----------

    @Transactional(readOnly = true)
    public List<HrDocType> getDocTypes() {
        return docTypeRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getDocCounts() {
        Map<Long, Long> m = new HashMap<>();
        for (HrDocType t : getDocTypes()) m.put(t.getId(), documentRepository.countByTypeId(t.getId()));
        return m;
    }

    public HrDocType createType(String name, String description, boolean required, DocScope scope, boolean employeeUpload) {
        HrDocType t = HrDocType.builder().sortOrder((int) docTypeRepository.count()).build();
        fillType(t, name, description, required, scope, employeeUpload);
        return docTypeRepository.save(t);
    }

    public void updateType(Long id, String name, String description, boolean required, DocScope scope, boolean employeeUpload) {
        fillType(getType(id), name, description, required, scope, employeeUpload);
    }

    // 사용 중지 / 다시 사용 (지우지 않는다 : 이미 받은 서류가 남아 있으므로)
    public HrDocType toggleType(Long id) {
        HrDocType t = getType(id);
        t.setActive(!t.isActive());
        return t;
    }

    private void fillType(HrDocType t, String name, String description, boolean required, DocScope scope, boolean employeeUpload) {
        String n = MemberService.emptyToNull(name);
        if (n == null) throw new BusinessException("서류 이름을 적어 주세요.");
        t.setName(n.length() > 40 ? n.substring(0, 40) : n);
        String d = MemberService.emptyToNull(description);
        t.setDescription(d == null ? null : (d.length() > 200 ? d.substring(0, 200) : d));
        t.setRequired(required);
        t.setScope(scope == null ? DocScope.SELF_ADMIN : scope);
        // 관리자만 볼 수 있는 서류는 직원이 낼 수 없다 (내고 나서 자기도 못 보게 되므로)
        t.setEmployeeUpload(employeeUpload && t.getScope().isSelf());
    }

    @Transactional(readOnly = true)
    public HrDocType getType(Long id) {
        return docTypeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("서류 종류를 찾을 수 없어요."));
    }

    // 처음 실행 : 기본 서류 종류
    public void ensureDefaultDocTypes() {
        if (docTypeRepository.count() > 0) return;
        log.info("=======> 기본 인사 서류 종류 만들기");
        createType("근로계약서", "입사 때 받은 근로계약서 (관리자가 올려요)", true, DocScope.SELF_ADMIN, false);
        createType("통장 사본", "급여 받을 통장 첫 장", true, DocScope.SELF_ADMIN, true);
        createType("주민등록 등본", "최근 3개월 안에 뗀 것", true, DocScope.SELF_ADMIN, true);
        createType("개인정보 수집·이용 동의서", "서명한 동의서", true, DocScope.SELF_LEADER_ADMIN, true);
        createType("가족관계증명서", "가족수당·연말정산이 필요할 때", false, DocScope.SELF_ADMIN, true);
        createType("보건증", "매장 근무자 (유효기간을 함께 적어 주세요)", false, DocScope.SELF_LEADER_ADMIN, true);
        createType("급여 정보", "연봉 계약서·급여 변경 내역", false, DocScope.ADMIN_ONLY, false);
    }

    // ---------- 직원 서류 ----------

    // 보는 사람 기준 서류 목록 : 관리자는 전부, 그 외에는 볼 수 있는 종류만
    @Transactional(readOnly = true)
    public List<DocRow> getDocRows(Member member, Member viewer) {
        Map<Long, HrDocument> mine = documentRepository.findByMemberId(member.getId()).stream()
                .collect(Collectors.toMap(d -> d.getType().getId(), d -> d));
        List<DocRow> rows = new ArrayList<>();
        for (HrDocType t : docTypeRepository.findByActiveTrueOrderBySortOrderAscIdAsc()) {
            boolean view = canViewType(t, member, viewer);
            if (!view) continue;
            boolean upload = canUpload(t, member, viewer);
            HrDocument d = mine.get(t.getId());
            rows.add(new DocRow(t, d, true, upload, d != null && upload));
        }
        return rows;
    }

    public HrDocument upload(Long memberId, Long typeId, MultipartFile file, LocalDate expiresAt, Member actor) {
        if (file == null || file.isEmpty()) throw new BusinessException("올릴 파일을 골라 주세요.");
        Member member = memberService.getMember(memberId);
        HrDocType type = getType(typeId);
        if (!type.isActive()) throw new BusinessException("사용 중지된 서류 종류예요.");
        if (!canUpload(type, member, actor)) throw new BusinessException("이 서류를 올릴 권한이 없어요.");
        FileRef ref = fileStore.storePrivate(file, "hr");
        HrDocument doc = documentRepository.findByMemberIdAndTypeId(memberId, typeId).orElse(null);
        boolean replace = doc != null;
        if (doc == null) {
            doc = HrDocument.builder().member(member).type(type).build();
        } else {
            fileStore.deletePrivateAfterCommit(doc.getFile() == null ? null : doc.getFile().getPath());
        }
        doc.setFile(ref);
        doc.setExpiresAt(expiresAt);
        doc.setUploadedBy(actor);
        doc.setUploadedAt(LocalDateTime.now());
        documentRepository.save(doc);
        historyService.record(member, HistoryField.DOCUMENT, replace ? type.getName() + " (이전 파일)" : null,
                type.getName() + (replace ? " 교체" : " 제출"), actor);
        return doc;
    }

    public String deleteDocument(Long docId, Member actor) {
        HrDocument doc = getDocument(docId);
        if (!canUpload(doc.getType(), doc.getMember(), actor)) throw new BusinessException("이 서류를 지울 권한이 없어요.");
        fileStore.deletePrivateAfterCommit(doc.getFile() == null ? null : doc.getFile().getPath());
        documentRepository.delete(doc);
        historyService.record(doc.getMember(), HistoryField.DOCUMENT, doc.getType().getName(), doc.getType().getName() + " 삭제", actor);
        return doc.getType().getName();
    }

    @Transactional(readOnly = true)
    public HrDocument getDocument(Long id) {
        return documentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("서류를 찾을 수 없어요."));
    }

    // 내려받기 : 권한 검사 후 실제 파일 위치
    @Transactional(readOnly = true)
    public Path openFile(Long docId, Member viewer) {
        HrDocument doc = getDocument(docId);
        if (!canViewType(doc.getType(), doc.getMember(), viewer)) {
            log.warn("=======> 인사 서류 열람 거부 : doc={} viewer={}", docId, viewer.getUsername());
            throw new BusinessException("이 서류를 볼 권한이 없어요.");
        }
        Path p = doc.getFile() == null ? null : fileStore.resolvePrivate(doc.getFile().getPath());
        if (p == null || !p.toFile().exists()) throw new EntityNotFoundException("파일을 찾을 수 없어요.");
        return p;
    }

    // 열람 권한 : 관리자 언제나 / 본인(scope.self) / 리더 = 같은 팀 매니저(scope.leader)
    public boolean canViewType(HrDocType t, Member member, Member viewer) {
        if (viewer == null) return false;
        if (viewer.isAdmin()) return true;
        if (viewer.getId().equals(member.getId())) return t.getScope().isSelf();
        return t.getScope().isLeader() && viewer.isManager() && viewer.getTeam() != null && member.getTeam() != null
                && viewer.getTeam().getId().equals(member.getTeam().getId());
    }

    // 올리기·지우기 : 관리자 언제나 / 본인은 직원 제출 가능 서류만
    public boolean canUpload(HrDocType t, Member member, Member actor) {
        if (actor == null) return false;
        if (actor.isAdmin()) return true;
        return actor.getId().equals(member.getId()) && t.isEmployeeUpload() && t.getScope().isSelf();
    }

    // 미제출 필수 서류 수 (직원별)
    @Transactional(readOnly = true)
    public Map<Long, Integer> getMissingRequiredCounts(List<Member> members) {
        List<HrDocType> required = docTypeRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream().filter(HrDocType::isRequired).toList();
        Map<Long, Set<Long>> has = new HashMap<>();
        for (HrDocument d : documentRepository.findByTypeRequiredTrueAndTypeActiveTrue()) {
            has.computeIfAbsent(d.getMember().getId(), k -> new HashSet<>()).add(d.getType().getId());
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Member m : members) {
            Set<Long> got = has.getOrDefault(m.getId(), Set.of());
            out.put(m.getId(), (int) required.stream().filter(t -> !got.contains(t.getId())).count());
        }
        return out;
    }

    // ---------- 알림 : 계약 만료 · 복직 예정 (오늘부터 30일 안) ----------

    @Transactional(readOnly = true)
    public List<HrAlert> getAlerts() {
        LocalDate today = LocalDate.now(), until = today.plusDays(ALERT_DAYS);
        List<HrAlert> out = new ArrayList<>();
        for (Member m : memberService.getActiveMembers()) {
            if (within(m.getContractEndDate(), today, until)) {
                out.add(new HrAlert(m, "계약 만료", m.getContractEndDate(), ChronoUnit.DAYS.between(today, m.getContractEndDate())));
            }
            if (within(m.getReturnDate(), today, until)) {
                out.add(new HrAlert(m, "복직 예정", m.getReturnDate(), ChronoUnit.DAYS.between(today, m.getReturnDate())));
            }
        }
        out.sort(Comparator.comparing(HrAlert::date));
        return out;
    }

    private static boolean within(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    // ---------- 근태 요약 (직원 허브) ----------

    // 날짜별 : 승인 휴가 > 출근(스케줄 시작보다 늦으면 지각) > 근무일인데 기록 없음 = 결근 > 휴무
    //  근무일 = 그 날 스케줄(휴무 제외)이 있거나, 이번 달 스케줄이 하나도 없으면 근무제의 근무 요일
    @Transactional(readOnly = true)
    public AttendanceMonth getAttendanceMonth(Member m, YearMonth month) {
        LocalDate first = month.atDay(1), last = month.atEndOfMonth(), today = LocalDate.now();
        Map<LocalDate, Attendance> att = attendanceRepository
                .findByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(m.getId(), first, last).stream()
                .collect(Collectors.toMap(Attendance::getWorkDate, a -> a, (a, b) -> a));
        Map<LocalDate, List<WorkSchedule>> sch = workScheduleRepository
                .findByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(m.getId(), first, last).stream()
                .collect(Collectors.groupingBy(WorkSchedule::getWorkDate));
        List<LeaveRequest> leaves = leaveRequestRepository.findByMemberIdOrderByIdDesc(m.getId()).stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED).toList();
        boolean hasSchedules = !sch.isEmpty();
        WorkPolicy policy = m.getWorkPolicy();

        int present = 0, late = 0, absent = 0, leave = 0;
        List<List<Day>> weeks = new ArrayList<>();
        LocalDate start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<Day> week = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Day day;
            if (d.isBefore(first) || d.isAfter(last)) {
                day = new Day(d, "OUT", "", null);
            } else {
                final LocalDate dd = d;
                LeaveRequest lv = leaves.stream().filter(l -> !dd.isBefore(l.getStartDate()) && !dd.isAfter(l.getEndDate())).findFirst().orElse(null);
                List<WorkSchedule> todays = sch.getOrDefault(d, List.of()).stream().filter(s -> !s.isOff()).toList();
                boolean workday = hasSchedules ? !todays.isEmpty() : (policy != null && policy.worksOn(d.getDayOfWeek()));
                Attendance a = att.get(d);
                if (lv != null) {
                    day = new Day(d, "LEAVE", "휴가", lv.getType().getLabel());
                    leave++;
                } else if (a != null && a.getClockIn() != null) {
                    LocalTime planned = !todays.isEmpty() ? todays.get(0).getStartTime() : (policy != null ? policy.getStartTime() : null);
                    boolean isLate = planned != null && a.getClockIn().toLocalTime().isAfter(planned.plusMinutes(1));
                    String in = a.getClockIn().toLocalTime().withSecond(0).withNano(0).toString();
                    day = new Day(d, isLate ? "LATE" : "PRESENT", isLate ? "지각" : "출근", "출근 " + in);
                    if (isLate) late++; else present++;
                } else if (d.isAfter(today) || d.equals(today)) {
                    day = new Day(d, workday ? "PLANNED" : "NONE", workday ? "예정" : "", null);
                } else if (workday) {
                    day = new Day(d, "ABSENT", "결근", "출근 기록 없음");
                    absent++;
                } else {
                    day = new Day(d, "OFF", "휴무", null);
                }
            }
            week.add(day);
            if (week.size() == 7) {
                weeks.add(week);
                week = new ArrayList<>();
            }
        }
        return new AttendanceMonth(month, weeks, present, late, absent, leave);
    }
}
