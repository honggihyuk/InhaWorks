package com.mysite.sbb.hr.repository;

import com.mysite.sbb.hr.entity.HrDocType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrDocTypeRepository extends JpaRepository<HrDocType, Long> {
    List<HrDocType> findAllByOrderBySortOrderAscIdAsc();
    List<HrDocType> findByActiveTrueOrderBySortOrderAscIdAsc();
}
