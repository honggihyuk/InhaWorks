package com.mysite.sbb.todo.dto;

import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.entity.TodoAssignment;
import com.mysite.sbb.todo.entity.TodoTask;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 관리자 대시보드 : 할 일 하나의 그 날짜 현황 (완료율 + 상태별 인원 + 직원별 상태)
public record TaskDashboard(TodoTask task,
                            LocalDate date,
                            List<TodoAssignment> assignments,
                            Map<AssignmentStatus, Long> counts,
                            long total,
                            long done,
                            int rate) {

    public long incomplete() {
        return total - done;
    }
}
