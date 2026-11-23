package com.mysite.sbb.chat.dto;

import com.mysite.sbb.member.entity.Member;

// 프로필 카드 : 사진, 이름, 직급, 근무지, 오늘 근무 상태, 근무제
public record MemberCard(Member member, String workStatus, String workStatusCss, boolean self) {
}
