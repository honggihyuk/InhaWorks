package com.mysite.sbb.admin.dto;

import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 관리자 화면 : 직원 등록 / 수정 폼
// 비밀번호는 등록할 때만 필수, 수정할 때는 비워 두면 그대로 둔다.
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "password")
public class AdminMemberDto {

    @NotBlank(message = "사용자ID는 필수항목입니다.")
    @Size(min = 3, max = 30, message = "사용자ID는 3~30자로 입력해주세요.")
    private String username;

    @NotBlank(message = "이름은 필수항목입니다.")
    @Size(max = 20)
    private String name;

    private String password;

    @Email(message = "이메일 형식이 아닙니다.")
    private String email;                   // 선택 항목

    private Long teamId;                    // 소속 팀 (선택)

    private Long workplaceId;               // 근무지 / 매장 (선택)

    private Long workPolicyId;              // 근무 정책 (근무제)

    @Size(max = 30)
    private String position;                // 직급

    @Size(max = 30)
    private String phone;

    @NotNull(message = "권한을 선택해주세요.")
    private MemberRole role = MemberRole.USER;

    private boolean active = true;

    // 수정 화면 : 직원 정보로 폼 채우기
    public static AdminMemberDto of(Member m) {
        AdminMemberDto dto = new AdminMemberDto();
        dto.setUsername(m.getUsername());
        dto.setName(m.getName());
        dto.setEmail(m.getEmail());
        dto.setTeamId(m.getTeam() == null ? null : m.getTeam().getId());
        dto.setWorkplaceId(m.getWorkplace() == null ? null : m.getWorkplace().getId());
        dto.setWorkPolicyId(m.getWorkPolicy() == null ? null : m.getWorkPolicy().getId());
        dto.setPosition(m.getPosition());
        dto.setPhone(m.getPhone());
        dto.setRole(m.getRole() == null ? MemberRole.USER : m.getRole());
        dto.setActive(m.isActive());
        return dto;
    }
}
