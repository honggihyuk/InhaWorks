package com.mysite.sbb.todo.dto;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.todo.entity.TodoItem;
import com.mysite.sbb.todo.entity.TodoTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 관리자 : 할 일 만들기 폼
// 항목은 같은 순서의 배열 4개로 받는다 (itemTypes[i], itemLabels[i], itemOptions[i], itemRequired[i])
@Getter
@Setter
@NoArgsConstructor
public class TodoTaskForm {

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 100)
    private String title;

    private String guide;

    @NotNull(message = "반복 주기를 골라 주세요.")
    private RepeatCycle cycle = RepeatCycle.DAILY;

    private List<DayOfWeek> days = new ArrayList<>();       // WEEKLY

    private Integer dayOfMonth;                             // MONTHLY

    @NotNull(message = "시작일을 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate = LocalDate.now();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Long reviewerId;

    private List<Long> memberIds = new ArrayList<>();

    private List<Long> workplaceIds = new ArrayList<>();

    private List<String> itemTypes = new ArrayList<>();
    private List<String> itemLabels = new ArrayList<>();
    private List<String> itemOptions = new ArrayList<>();
    private List<String> itemRequired = new ArrayList<>();  // "Y" / "N"
    private List<Long> itemIds = new ArrayList<>();         // 수정 : 기존 항목 ID (새 항목은 빈 값)

    private List<String> removeGuideFiles = new ArrayList<>();   // 수정 : 지울 가이드 첨부 (경로)

    public static String at(List<String> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    // 화면(검증 오류로 다시 보여줄 때) 에서 i 번째 항목 값 꺼내기
    public String typeAt(int i) {
        return at(itemTypes, i);
    }

    public String optionAt(int i) {
        return at(itemOptions, i);
    }

    public String requiredAt(int i) {
        return at(itemRequired, i);
    }

    public Long itemIdAt(int i) {
        return (itemIds != null && i < itemIds.size()) ? itemIds.get(i) : null;
    }

    // 수정 화면 : 저장된 할 일로 채우기 (숨긴 항목은 빼고)
    public static TodoTaskForm of(TodoTask t) {
        TodoTaskForm f = new TodoTaskForm();
        f.setTitle(t.getTitle());
        f.setGuide(t.getGuide());
        f.setCycle(t.getCycle());
        f.setDays(new ArrayList<>(t.getDays()));
        f.setDayOfMonth(t.getDayOfMonth());
        f.setStartDate(t.getStartDate());
        f.setEndDate(t.getEndDate());
        f.setReviewerId(t.getReviewer() == null ? null : t.getReviewer().getId());
        t.getAssignees().forEach(m -> f.getMemberIds().add(m.getId()));
        t.getWorkplaces().forEach(w -> f.getWorkplaceIds().add(w.getId()));
        for (TodoItem item : t.getActiveItems()) {
            f.getItemIds().add(item.getId());
            f.getItemTypes().add(item.getType().name());
            f.getItemLabels().add(item.getLabel());
            f.getItemOptions().add(item.getOptions());
            f.getItemRequired().add(item.isRequired() ? "Y" : "N");
        }
        return f;
    }
}
