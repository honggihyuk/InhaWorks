package com.mysite.sbb.todo.repository;

import com.mysite.sbb.todo.entity.TodoTaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TodoTaskCommentRepository extends JpaRepository<TodoTaskComment, Long> {
    List<TodoTaskComment> findByTaskIdOrderByIdAsc(Long taskId);
}
