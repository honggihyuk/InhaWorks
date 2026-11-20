package com.mysite.sbb.attendance.dto;

import com.mysite.sbb.attendance.entity.WorkPolicy;
import com.mysite.sbb.member.entity.Member;

import java.util.List;

// 스케줄 화면 위쪽의 근무제 탭 하나 : 정책 + 소속 직원(재직 중)
public record PolicyTab(WorkPolicy policy, List<Member> members) {

    public int count() {
        return members.size();
    }

    // 탭에 겹쳐 보여 줄 앞의 3명
    public List<Member> faces() {
        return members.size() <= 3 ? members : members.subList(0, 3);
    }
}
