package com.mysite.sbb.todo.entity;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.util.RecurrenceRule;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.organization.entity.Workplace;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

// 반복 할 일 (관리자가 만든다)
//  예) "매장 오픈 체크리스트" : 매일, 인하점 직원 전체, 검수자 김민수
//  스케줄러가 날짜마다 대상 직원별 TodoAssignment 를 만든다 (TodoAssignmentGenerator)
@Entity
@Getter
@Setter
@ToString(of = {"id", "title"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String guide;                   // 가이드 (어떻게 하면 되는지)

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "todo_task_file", joinColumns = @JoinColumn(name = "task_id"))
    private List<FileRef> guideFiles = new ArrayList<>();   // 가이드 첨부

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 20)
    private RepeatCycle cycle;              // DAILY / WEEKLY / MONTHLY

    @Column(length = 100)
    private String daysOfWeek;              // WEEKLY : "MONDAY,WEDNESDAY"

    private Integer dayOfMonth;             // MONTHLY : 1 ~ 31

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;              // 비우면 계속

    @ManyToOne(fetch = FetchType.LAZY)
    private Member reviewer;                // 검수자 (없으면 제출하면 바로 '제출 완료')

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "todo_task_member", joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<Member> assignees = new LinkedHashSet<>();          // 직원 단위 배정

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "todo_task_workplace", joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "workplace_id"))
    private Set<Workplace> workplaces = new LinkedHashSet<>();      // 근무지(매장) 단위 배정

    @Builder.Default
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<TodoItem> items = new ArrayList<>();

    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member createdBy;

    private LocalDateTime created;

    // 삭제 (지난 기록을 남기기 위해 행은 두고 목록·배치에서 뺀다)
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean deleted;

    // 지금 쓰는 항목 (수정 때 지워서 숨긴 항목은 빼고)
    public List<TodoItem> getActiveItems() {
        return items.stream().filter(i -> !i.isArchived()).toList();
    }

    public Set<DayOfWeek> getDays() {
        return RecurrenceRule.parseDays(daysOfWeek);
    }

    public String getCycleText() {
        return RecurrenceRule.describe(cycle, getDays(), dayOfMonth);
    }

    public boolean occursOn(LocalDate date) {
        return active && !deleted && RecurrenceRule.occursOn(cycle, getDays(), dayOfMonth, startDate, endDate, date);
    }

    public void addItem(TodoItem item) {
        item.setTask(this);
        item.setSortOrder(items.size());
        items.add(item);
    }

    // 배정 대상 표시 (예: 인하점 · 박지훈)
    public String getTargetText() {
        List<String> names = new ArrayList<>();
        workplaces.forEach(w -> names.add(w.getName()));
        assignees.forEach(m -> names.add(m.getDisplayName()));
        return String.join(" · ", names);
    }
}
