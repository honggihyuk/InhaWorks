package com.mysite.sbb.report.repository;

import com.mysite.sbb.report.entity.ReportComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportCommentRepository extends JpaRepository<ReportComment, Long> {
    List<ReportComment> findByReportIdOrderByIdAsc(Long reportId);
}
