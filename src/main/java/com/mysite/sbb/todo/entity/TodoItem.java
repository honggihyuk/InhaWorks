package com.mysite.sbb.todo.entity;

import com.mysite.sbb.todo.constant.TodoItemType;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

// 할 일 안의 항목 하나 (예/아니오, 체크박스, 사진, 서명, 객관식, 주관식)
@Entity
@Getter
@Setter
@ToString(exclude = "task")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TodoTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private TodoItemType type;

    @Column(nullable = false, length = 200)
    private String label;                   // 질문 (예: 출입문·조명 점검했나요?)

    @Column(length = 500)
    private String options;                 // 객관식 선택지 "상,중,하"

    private boolean required;

    private int sortOrder;

    // 수정할 때 지운 항목 : 이미 낸 답이 있으면 지우지 않고 숨긴다 (지난 답 보기 · 엑셀에는 남는다)
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean archived;

    public List<String> getOptionList() {
        if (options == null || options.isBlank()) return List.of();
        return Arrays.stream(options.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
