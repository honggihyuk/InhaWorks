package com.mysite.sbb.admin.service;

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
        getMember(id).setRole(role);
    }

    private void copy(AdminMemberDto dto, Member member) {
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
    }

    // 퇴사 처리 / 복직
    //  - 할 일 제출, 보고서, 게시글처럼 회사 기록이 직원에 연결되어 있으므로 지우지 않고 비활성화한다
    //  - 비활성화된 직원은 로그인할 수 없고(MemberSecurityService), 새 할 일도 배정되지 않는다
    public void toggleActive(Long id) {
        Member member = getMember(id);
        member.setActive(!member.isActive());
    }
}
