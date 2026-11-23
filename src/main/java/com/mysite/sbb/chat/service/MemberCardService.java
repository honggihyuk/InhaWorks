package com.mysite.sbb.chat.service;

import com.mysite.sbb.attendance.constant.LeaveStatus;
import com.mysite.sbb.attendance.entity.Attendance;
import com.mysite.sbb.attendance.repository.AttendanceRepository;
import com.mysite.sbb.attendance.repository.LeaveRequestRepository;
import com.mysite.sbb.chat.dto.MemberCard;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 프로필 카드 : 근무 상태 = 휴가 / 퇴근 / 근무 중 / 출근 전 (오늘 기록으로 계산, CHT-11)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberCardService {

    private final MemberRepository memberRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRepository;

    public MemberCard card(Long id, Long meId) {
        Member m = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
        if (m.getWorkPolicy() != null) m.getWorkPolicy().getName();     // 화면에서 쓰는 값 미리 읽기
        if (m.getTeam() != null) m.getTeam().getName();
        if (m.getWorkplace() != null) m.getWorkplace().getName();
        String status = workStatus(m, LocalDate.now());
        String css = switch (status) {
            case "근무 중" -> "solid";
            case "휴가" -> "orange";
            case "퇴사" -> "red";
            default -> "grey";
        };
        return new MemberCard(m, status, css, m.getId().equals(meId));
    }

    public String workStatus(Member m, LocalDate today) {
        if (!m.isActive()) return "퇴사";
        boolean onLeave = leaveRepository.findByMemberIdOrderByIdDesc(m.getId()).stream()
                .anyMatch(l -> l.getStatus() == LeaveStatus.APPROVED && !today.isBefore(l.getStartDate()) && !today.isAfter(l.getEndDate()));
        if (onLeave) return "휴가";
        Attendance a = attendanceRepository.findByMemberIdAndWorkDate(m.getId(), today).orElse(null);
        if (a == null || a.getClockIn() == null) return "출근 전";
        return a.getClockOut() == null ? "근무 중" : "퇴근";
    }
}
