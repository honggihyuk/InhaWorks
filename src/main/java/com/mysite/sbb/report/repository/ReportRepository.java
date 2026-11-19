package com.mysite.sbb.report.repository;

import com.mysite.sbb.report.constant.ReportStatus;
import com.mysite.sbb.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findTop50ByAuthorIdOrderByIdDesc(Long authorId);
    List<Report> findByFormIdOrderByIdDesc(Long formId);
    long countByFormId(Long formId);
    Optional<Report> findBySubmitToken(String submitToken);

    // 관리자 대시보드 : 결재 대기 보고서 수
    long countByStatusIn(Collection<ReportStatus> statuses);
}
