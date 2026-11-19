package com.mysite.sbb.todo.dto;

import com.mysite.sbb.todo.entity.TodoTask;

// 관리자 할 일 목록 : 오늘 완료율
public record TaskSummary(TodoTask task, long total, long done, int rate) {
}
