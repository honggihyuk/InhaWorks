package com.mysite.sbb.attendance.repository;

import com.mysite.sbb.attendance.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    List<WorkSchedule> findByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(Long memberId, LocalDate from, LocalDate to);
    List<WorkSchedule> findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(LocalDate from, LocalDate to);
    List<WorkSchedule> findByShiftTypeId(Long shiftTypeId);
    List<WorkSchedule> findByShiftTypeIsNull();
}
