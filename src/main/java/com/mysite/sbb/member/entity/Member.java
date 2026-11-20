package com.mysite.sbb.member.entity;

import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.hr.constant.EmploymentType;
import java.time.LocalDate;

import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.entity.Workplace;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 직원 (관리자 포함)
@Entity
@Getter
@Setter
@ToString(exclude = {"password", "team", "workplace", "workPolicy"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;                // 사용자 ID (로그인 아이디 → 중복 불가)

    private String password;                // 비밀번호 (BCrypt 로 암호화해서 저장)

    @Column(length = 20)
    private String name;                    // 화면에 보이는 이름 (예: 홍기혁)

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;                      // 소속 팀

    @ManyToOne(fetch = FetchType.LAZY)
    private Workplace workplace;            // 근무지 / 매장

    @ManyToOne(fetch = FetchType.LAZY)
    private WorkPolicy workPolicy;          // 근무 정책 (근무제). 처음 실행할 때 기본 정책이 배정된다

    @Column(name = "job_position", length = 30)
    private String position;                // 직급 (예: 사원, 매니저)

    @Column(length = 30)
    private String phone;                   // 연락처 (선택)

    private String photoPath;               // 프로필 사진 경로 (/uploads/...)

    @Column(unique = true)
    private String email;                   // 이메일 (선택, 안 쓰면 null)

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MemberRole role;                // 권한 (USER : 직원, MANAGER : 매니저, ADMIN : 관리자)

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;          // 재직 중 (퇴사 처리하면 false → 로그인 불가, 할 일 배정 제외)

    private LocalDateTime created;          // 생성일

    // ---------- 인사 정보 ----------
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmploymentType employmentType;  // 고용 형태 (정규직, 계약직 ...)

    private LocalDate hireDate;             // 입사일

    private LocalDate contractEndDate;      // 계약 만료일 (계약직·인턴)

    private LocalDate returnDate;           // 복직 예정일 (휴직 중)

    // 근속 : "2년 3개월" (입사일이 없으면 null)
    public String getTenureText() {
        if (hireDate == null || hireDate.isAfter(LocalDate.now())) return null;
        java.time.Period p = java.time.Period.between(hireDate, LocalDate.now());
        if (p.getYears() == 0 && p.getMonths() == 0) return p.getDays() + "일";
        return (p.getYears() > 0 ? p.getYears() + "년 " : "") + p.getMonths() + "개월";
    }

    // 화면 표시용 이름 : 이름이 없으면 아이디
    public String getDisplayName() {
        return (name == null || name.isBlank()) ? username : name;
    }

    // 프로필 동그라미에 넣을 글자 (이름 뒤 2글자, 예: 홍기혁 → 기혁)
    public String getInitials() {
        String n = getDisplayName();
        return n.length() <= 2 ? n : n.substring(n.length() - 2);
    }

    // 내 정보 화면 부제 (예: 영업팀 · 인하점 · 사원)
    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        if (team != null) sb.append(team.getName());
        if (workplace != null) sb.append(sb.isEmpty() ? "" : " · ").append(workplace.getName());
        if (position != null && !position.isBlank()) sb.append(sb.isEmpty() ? "" : " · ").append(position);
        return sb.toString();
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public boolean isManager() {
        return role == MemberRole.MANAGER;
    }
}
