package com.mysite.sbb.todo.repository;

import com.mysite.sbb.todo.entity.TodoAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoAnswerRepository extends JpaRepository<TodoAnswer, Long> {

    // 할 일 수정 : 이 항목에 낸 답이 있으면 항목을 지우지 않고 숨긴다
    boolean existsByItemId(Long itemId);
}
