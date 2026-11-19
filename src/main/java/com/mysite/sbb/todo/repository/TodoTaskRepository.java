package com.mysite.sbb.todo.repository;

import com.mysite.sbb.todo.entity.TodoTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TodoTaskRepository extends JpaRepository<TodoTask, Long> {

    List<TodoTask> findAllByOrderByIdDesc();

    // 목록 : 삭제한 할 일은 빼고
    List<TodoTask> findByDeletedFalseOrderByIdDesc();

    // 그 날짜가 기간 안에 들어가는 사용 중인 할 일 (요일·날짜 규칙은 RecurrenceRule 로 한 번 더 거른다)
    @Query("select t from TodoTask t where t.active = true and t.deleted = false and t.startDate <= :d and (t.endDate is null or t.endDate >= :d)")
    List<TodoTask> findActiveOn(@Param("d") LocalDate date);
}
