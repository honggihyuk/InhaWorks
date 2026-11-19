package com.mysite.sbb.report.repository;

import com.mysite.sbb.report.entity.ReportAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportAnswerRepository extends JpaRepository<ReportAnswer, Long> {

    // 양식 수정 : 이 항목에 낸 답이 있으면 항목을 지우지 않고 숨긴다
    boolean existsByFieldId(Long fieldId);
}
