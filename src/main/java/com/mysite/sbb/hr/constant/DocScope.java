package com.mysite.sbb.hr.constant;

import lombok.Getter;

// 인사 서류 열람 권한 : 누가 볼 수 있는지 (관리자는 언제나 볼 수 있다)
//  리더 = 같은 팀의 매니저(MemberRole.MANAGER)
@Getter
public enum DocScope {
    SELF_LEADER_ADMIN("본인 · 리더 · 관리자", true, true),
    SELF_ADMIN("본인 · 관리자", true, false),
    ADMIN_ONLY("관리자만", false, false);

    private final String label;
    private final boolean self;
    private final boolean leader;

    DocScope(String label, boolean self, boolean leader) {
        this.label = label;
        this.self = self;
        this.leader = leader;
    }
}
