package com.mysite.sbb.admin.service;

import com.mysite.sbb.hr.constant.HistoryField;
import com.mysite.sbb.hr.service.HrHistoryService;
import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.attendance.service.WorkPolicyService;
import com.mysite.sbb.admin.dto.AdminMemberDto;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 관리자 : 직원 등록 / 수정 / 퇴사 처리
@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationService organizationService;
    private final WorkPolicyService workPolicyService;
    private final HrHistoryService historyService;

    @Transactional(readOnly = true)
    public Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
    }

    // 중복 확인 (수정할 때는 자기 자신은 빼고)
    @Transactional(readOnly = true)
    public boolean isUsernameTaken(String username, Long exceptId) {
        return memberRepository.findByUsername(username)
                .filter(m -> !m.getId().equals(exceptId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isEmailTaken(String email, Long exceptId) {
        return memberRepository.findByEmail(email)
                .filter(m -> !m.getId().equals(exceptId))
                .isPresent();
    }

    public Member create(AdminMemberDto dto) {
        Member member = Member.builder()
                .password(passwordEncoder.encode(dto.getPassword()))
                .created(LocalDateTime.now())
                .build();
        copy(dto, member);
        return memberRepository.save(member);
    }

    public void update(Long id, AdminMemberDto dto) {
        Member member = getMember(id);
        copy(dto, member);
        // 비밀번호 칸을 채웠을 때만 바꾼다 (비밀번호 초기화)
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            member.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
    }

    // 목록에서 바로 권한 바꾸기
    public void updateRole(Long id, com.mysite.sbb.member.constant.MemberRole role) {
        Member m = getMember(id);
        historyService.record(m, HistoryField.ROLE, label(m.getRole()), label(role));
        m.setRole(role);
    }

    private static String label(com.mysite.sbb.member.constant.MemberRole r) {
        return r == null ? null : r.getLabel();
    }

    private void copy(AdminMemberDto dto, Member member) {
        // 인사 변경 이력 : 저장 전 값 (새 직원이면 기록하지 않는다)
        boolean existing = member.getId() != null;
        String team0 = member.getTeam() == null ? null : member.getTeam().getName();
        String place0 = member.getWorkplace() == null ? null : member.getWorkplace().getName();
        String pos0 = member.getPosition();
        String role0 = label(member.getRole());
        String policy0 = member.getWorkPolicy() == null ? null : member.getWorkPolicy().getName();
        String emp0 = member.getEmploymentType() == null ? null : member.getEmploymentType().getLabel();
        java.time.LocalDate hire0 = member.getHireDate(), contract0 = member.getContractEndDate(), return0 = member.getReturnDate();
        boolean active0 = member.isActive();
        member.setUsername(dto.getUsername().strip());
        member.setName(dto.getName().strip());
        member.setEmail(MemberService.emptyToNull(dto.getEmail()));
        member.setTeam(organizationService.getTeam(dto.getTeamId()));
        member.setWorkplace(organizationService.getWorkplace(dto.getWorkplaceId()));
        // 근무 정책 : 고르지 않으면 그대로 두고, 새 직원이면 첫 정책
        WorkPolicy policy = workPolicyService.findOrNull(dto.getWorkPolicyId());
        if (policy != null) member.setWorkPolicy(policy);
        else if (member.getWorkPolicy() == null && !workPolicyService.getPolicies().isEmpty()) member.setWorkPolicy(workPolicyService.getPolicies().get(0));
        member.setPosition(MemberService.emptyToNull(dto.getPosition()));
        member.setPhone(MemberService.emptyToNull(dto.getPhone()));
        member.setRole(dto.getRole());
        member.setActive(dto.isActive());
        member.setEmploymentType(dto.getEmploymentType());
        member.setHireDate(dto.getHireDate());
        member.setContractEndDate(dto.getContractEndDate());
        member.setReturnDate(dto.getReturnDate());
        if (existing) {
            historyService.record(member, HistoryField.TEAM, team0, member.getTeam() == null ? null : member.getTeam().getName());
            historyService.record(member, HistoryField.WORKPLACE, place0, member.getWorkplace() == null ? null : member.getWorkplace().getName());
            historyService.record(member, HistoryField.POSITION, pos0, member.getPosition());
            historyService.record(member, HistoryField.ROLE, role0, label(member.getRole()));
            historyService.record(member, HistoryField.WORK_POLICY, policy0, member.getWorkPolicy() == null ? null : member.getWorkPolicy().getName());
            historyService.record(member, HistoryField.EMPLOYMENT, emp0, member.getEmploymentType() == null ? null : member.getEmploymentType().getLabel());
            historyService.record(member, HistoryField.HIRE_DATE, hire0, member.getHireDate());
            historyService.record(member, HistoryField.CONTRACT_END, contract0, member.getContractEndDate());
            historyService.record(member, HistoryField.RETURN_DATE, return0, member.getReturnDate());
            historyService.record(member, HistoryField.STATUS, active0 ? "재직" : "퇴사", member.isActive() ? "재직" : "퇴사");
        }
    }

    // 퇴사 처리 / 복직
    //  - 할 일 제출, 보고서, 게시글처럼 회사 기록이 직원에 연결되어 있으므로 지우지 않고 비활성화한다
    //  - 비활성화된 직원은 로그인할 수 없고(MemberSecurityService), 새 할 일도 배정되지 않는다
    public void toggleActive(Long id) {
        Member member = getMember(id);
        historyService.record(member, HistoryField.STATUS, member.isActive() ? "재직" : "퇴사", member.isActive() ? "퇴사" : "재직");
        member.setActive(!member.isActive());
    }
}
