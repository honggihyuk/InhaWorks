package com.mysite.sbb.hr.service;

import com.mysite.sbb.hr.constant.HistoryField;
import com.mysite.sbb.hr.entity.MemberHistory;
import com.mysite.sbb.hr.repository.MemberHistoryRepository;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

// 인사 변경 이력 기록 : 값이 실제로 바뀐 경우에만 한 줄 남긴다
//  (직원 수정, 권한 변경, 퇴사·복직, 근무제 옮기기, 서류 제출·삭제에서 부른다)
@Service
@RequiredArgsConstructor
@Transactional
public class HrHistoryService {

    private final MemberHistoryRepository memberHistoryRepository;
    private final MemberRepository memberRepository;

    // 바꾼 사람 = 지금 로그인한 사람 (없으면 시스템)
    public void record(Member member, HistoryField field, Object before, Object after) {
        record(member, field, before, after, currentActor());
    }

    private Member currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return null;
        return memberRepository.findByUsername(auth.getName()).orElse(null);
    }

    public void record(Member member, HistoryField field, Object before, Object after, Member by) {
        String b = text(before), a = text(after);
        if (Objects.equals(b, a)) return;
        memberHistoryRepository.save(MemberHistory.builder().member(member).field(field)
                .beforeValue(b).afterValue(a).changedBy(by).changedAt(LocalDateTime.now()).build());
    }

    @Transactional(readOnly = true)
    public List<MemberHistory> getHistory(Long memberId) {
        return memberHistoryRepository.findByMemberIdOrderByChangedAtDescIdDesc(memberId);
    }

    private static String text(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        if (s.isBlank()) return null;
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
