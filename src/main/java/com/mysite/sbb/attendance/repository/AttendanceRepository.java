package com.mysite.sbb.attendance.repository;

import com.mysite.sbb.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByMemberIdAndWorkDate(Long memberId, LocalDate workDate);
    List<Attendance> findByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(Long memberId, LocalDate from, LocalDate to);
    List<Attendance> findByWorkDateOrderByClockInAsc(LocalDate workDate);
}
