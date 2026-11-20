package com.mysite.sbb.todo.repository;

import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.entity.TodoAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TodoAssignmentRepository extends JpaRepository<TodoAssignment, Long> {

    // 생성 배치 : 이미 만든 사람은 건너뛰기
    @Query("select a.member.id from TodoAssignment a where a.task.id = :taskId and a.workDate = :d")
    List<Long> findMemberIds(@Param("taskId") Long taskId, @Param("d") LocalDate date);

    // 직원 : 오늘 내 할 일
    List<TodoAssignment> findByMemberIdAndWorkDateOrderByIdAsc(Long memberId, LocalDate workDate);

    // 직원 : 지난 날짜에 아직 못 낸 / 재작업 필요한 할 일
    List<TodoAssignment> findByMemberIdAndWorkDateBeforeAndStatusInOrderByWorkDateDesc(Long memberId, LocalDate before,
                                                                                    Collection<AssignmentStatus> statuses);

    // 관리자 대시보드 : 할 일 + 날짜별 직원 현황
    List<TodoAssignment> findByTaskIdAndWorkDateOrderByIdAsc(Long taskId, LocalDate workDate);

    long countByTaskIdAndWorkDate(Long taskId, LocalDate workDate);

    // 마감 알림 : 그 날짜의 미완료
    List<TodoAssignment> findByWorkDateAndStatusIn(LocalDate workDate, Collection<AssignmentStatus> statuses);

    // 검수 대기함 : 내가 검수자인 할 일 / (관리자) 전체
    List<TodoAssignment> findByTaskReviewerIdAndStatusOrderByWorkDateDescIdDesc(Long reviewerId, AssignmentStatus status);

    List<TodoAssignment> findByStatusOrderByWorkDateDescIdDesc(AssignmentStatus status);

    long countByTaskReviewerIdAndStatus(Long reviewerId, AssignmentStatus status);

    long countByMemberIdAndWorkDateAndStatusIn(Long memberId, LocalDate workDate, Collection<AssignmentStatus> statuses);

    boolean existsByLastSubmitToken(String token);

    // 관리자 대시보드 : 기간의 모든 배정 (근무지별 현황, 이번 주 제출)
    List<TodoAssignment> findByWorkDateBetween(LocalDate from, LocalDate to);

    // 할 일 삭제 : 오늘부터 아직 제출하지 않은 배정은 함께 지운다
    List<TodoAssignment> findByTaskIdAndStatusAndWorkDateGreaterThanEqual(Long taskId, AssignmentStatus status, LocalDate from);

    // 직원 허브 업무 탭 : 최근 배정
    List<TodoAssignment> findTop20ByMemberIdOrderByWorkDateDescIdDesc(Long memberId);

    long countByMemberIdAndWorkDateBetweenAndStatus(Long memberId, LocalDate from, LocalDate to, AssignmentStatus status);

    long countByMemberIdAndWorkDateBetween(Long memberId, LocalDate from, LocalDate to);
}
