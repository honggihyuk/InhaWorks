package com.mysite.sbb.todo.repository;

import com.mysite.sbb.todo.entity.TodoComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TodoCommentRepository extends JpaRepository<TodoComment, Long> {
    List<TodoComment> findByAssignmentIdOrderByIdAsc(Long assignmentId);
}
