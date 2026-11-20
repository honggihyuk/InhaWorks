package com.mysite.sbb.hr.repository;

import com.mysite.sbb.hr.entity.HrDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HrDocumentRepository extends JpaRepository<HrDocument, Long> {
    List<HrDocument> findByMemberId(Long memberId);
    Optional<HrDocument> findByMemberIdAndTypeId(Long memberId, Long typeId);
    List<HrDocument> findByTypeRequiredTrueAndTypeActiveTrue();
    long countByTypeId(Long typeId);
}
