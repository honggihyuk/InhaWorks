package com.mysite.sbb.report.repository;

import com.mysite.sbb.report.entity.ReportForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportFormRepository extends JpaRepository<ReportForm, Long> {
    List<ReportForm> findAllByOrderByIdDesc();
    List<ReportForm> findByActiveTrueOrderByIdDesc();
}
