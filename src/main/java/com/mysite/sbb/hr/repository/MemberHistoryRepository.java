package com.mysite.sbb.hr.repository;

import com.mysite.sbb.hr.entity.MemberHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberHistoryRepository extends JpaRepository<MemberHistory, Long> {
    List<MemberHistory> findByMemberIdOrderByChangedAtDescIdDesc(Long memberId);
}
