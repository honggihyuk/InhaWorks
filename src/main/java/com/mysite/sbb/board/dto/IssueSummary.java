package com.mysite.sbb.board.dto;

// 이슈 & 해결 상단 통계
//  처리 대기(처리 중 포함) : 건수 · 미해결 기간 평균/최장 (일)
//  해결 완료 : 건수 · 해결 소요 기간 평균/최장 (일)
public record IssueSummary(long pending, long inProgress, double pendingAvg, double pendingMax,
                           long resolved, double resolvedAvg, double resolvedMax) {

    public long total() {
        return pending + resolved;
    }

    public int resolvedRate() {
        return total() == 0 ? 0 : (int) Math.round(resolved * 100.0 / total());
    }
}
