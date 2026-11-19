package com.mysite.sbb.member.constant;

import lombok.Getter;

// 로그인한 직원에게 부여할 권한
@Getter
public enum MemberRole {
    ADMIN("ROLE_ADMIN", "관리자"),     // 관리자 모드(/admin) : 직원·할 일·보고서·게시판·근태 관리
    MANAGER("ROLE_MANAGER", "매니저"), // 매장/팀 매니저 : 검수자·결재자로 지정되는 경우가 많다
    USER("ROLE_USER", "직원");

    private final String value;
    private final String label;

    MemberRole(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
