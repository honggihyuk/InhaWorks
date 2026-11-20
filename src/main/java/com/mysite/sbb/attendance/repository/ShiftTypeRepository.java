package com.mysite.sbb.attendance.repository;

import com.mysite.sbb.attendance.entity.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {
}
