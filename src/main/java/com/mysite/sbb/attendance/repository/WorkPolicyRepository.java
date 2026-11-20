package com.mysite.sbb.attendance.repository;

import com.mysite.sbb.attendance.entity.WorkPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkPolicyRepository extends JpaRepository<WorkPolicy, Long> {
    List<WorkPolicy> findAllByOrderBySortOrderAscIdAsc();
}
