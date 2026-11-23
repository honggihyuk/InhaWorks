package com.mysite.sbb.board.dto;

import com.mysite.sbb.board.constant.IssueCategory;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.entity.Post;

import java.time.LocalDate;

// 이슈 목록·내보내기가 함께 쓰는 필터 (지점, 상태, 분류, 기간)
//  status 값 : 비우면 전체, PENDING(또는 예전 OPEN) = 해결 완료가 아닌 전부, 그 밖에는 단계 이름
public record IssueFilter(String status, Long place, IssueCategory category, LocalDate from, LocalDate to) {

    public static final String PENDING = "PENDING";

    public IssueFilter {
        status = (status == null || status.isBlank()) ? null : status.strip().toUpperCase();
        if ("OPEN".equals(status)) status = PENDING;    // 예전 주소 호환 : '처리 대기' 칸
        if (status != null && !PENDING.equals(status)) IssueStatus.parse(status);   // 없는 값이면 400
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("기간의 시작이 끝보다 늦어요.");
        }
    }

    public boolean matches(Post p) {
        if (PENDING.equals(status) && p.getIssueStatus() == IssueStatus.RESOLVED) return false;
        if (status != null && !PENDING.equals(status) && p.getIssueStatus() != IssueStatus.parse(status)) return false;
        if (place != null && (p.getWorkplace() == null || !p.getWorkplace().getId().equals(place))) return false;
        if (category != null && p.getCategory() != category) return false;
        if (p.getCreated() == null) return from == null && to == null;
        LocalDate day = p.getCreated().toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }
}
