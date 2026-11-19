package com.mysite.sbb.attendance.repository;

import com.mysite.sbb.attendance.constant.LeaveStatus;
import com.mysite.sbb.attendance.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByMemberIdOrderByIdDesc(Long memberId);
    List<LeaveRequest> findByStatusOrderByStartDateAsc(LeaveStatus status);
    List<LeaveRequest> findTop50ByOrderByIdDesc();
}
