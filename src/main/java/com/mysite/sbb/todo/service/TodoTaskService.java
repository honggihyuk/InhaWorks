package com.mysite.sbb.todo.service;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.common.util.RecurrenceRule;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.organization.repository.WorkplaceRepository;
import com.mysite.sbb.outbox.service.OutboxService;
import com.mysite.sbb.todo.constant.AssignmentStatus;
import com.mysite.sbb.todo.constant.TodoItemType;
import com.mysite.sbb.todo.dto.TaskSummary;
import com.mysite.sbb.todo.dto.TodoTaskForm;
import com.mysite.sbb.todo.entity.TodoAssignment;
import com.mysite.sbb.todo.entity.TodoItem;
import com.mysite.sbb.todo.entity.TodoTask;
import com.mysite.sbb.todo.entity.TodoTaskComment;
import com.mysite.sbb.todo.repository.TodoAnswerRepository;
import com.mysite.sbb.todo.repository.TodoCommentRepository;
import com.mysite.sbb.todo.repository.TodoTaskCommentRepository;
import com.mysite.sbb.todo.repository.TodoAssignmentRepository;
import com.mysite.sbb.todo.repository.TodoTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 관리자 : 반복 할 일 만들기 / 수정 / 삭제 / 목록 / 사용 중지 / 할 일 단위 댓글
@Service
@RequiredArgsConstructor
@Transactional
public class TodoTaskService {

    private final TodoTaskRepository todoTaskRepository;
    private final TodoAssignmentRepository todoAssignmentRepository;
    private final TodoAssignmentGenerator generator;
    private final MemberService memberService;
    private final WorkplaceRepository workplaceRepository;
    private final FileStore fileStore;
    private final OutboxService outboxService;
    private final TodoAnswerRepository todoAnswerRepository;
    private final TodoCommentRepository todoCommentRepository;
    private final TodoTaskCommentRepository todoTaskCommentRepository;

    @Transactional(readOnly = true)
    public TodoTask get(Long id) {
        return todoTaskRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("할 일을 찾을 수 없어요. (삭제됐을 수 있어요)"));
    }

    // 목록 + 그 날짜 완료율
    @Transactional(readOnly = true)
    public List<TaskSummary> getSummaries(LocalDate date) {
        List<TaskSummary> list = new ArrayList<>();
        for (TodoTask task : todoTaskRepository.findByDeletedFalseOrderByIdDesc()) {
            List<TodoAssignment> as = todoAssignmentRepository.findByTaskIdAndWorkDateOrderByIdAsc(task.getId(), date);
            long done = as.stream().filter(a -> a.getStatus().isDone()).count();
            list.add(new TaskSummary(task, as.size(), done, as.isEmpty() ? 0 : (int) Math.round(done * 100.0 / as.size())));
        }
        return list;
    }

    public TodoTask create(TodoTaskForm form, List<MultipartFile> guideFiles, String adminUsername) {
        validate(form);
        TodoTask task = TodoTask.builder()
                .title(form.getTitle().strip())
                .guide(form.getGuide())
                .cycle(form.getCycle())
                .daysOfWeek(form.getCycle() == RepeatCycle.WEEKLY ? RecurrenceRule.joinDays(form.getDays()) : null)
                .dayOfMonth(form.getCycle() == RepeatCycle.MONTHLY ? form.getDayOfMonth() : null)
                .startDate(form.getStartDate())
                .endDate(form.getEndDate())
                .reviewer(form.getReviewerId() == null ? null : memberService.getMember(form.getReviewerId()))
                .createdBy(memberService.getMember(adminUsername))
                .created(LocalDateTime.now())
                .build();
        task.getAssignees().addAll(memberService.getMembers(form.getMemberIds()));
        if (form.getWorkplaceIds() != null && !form.getWorkplaceIds().isEmpty()) {
            task.getWorkplaces().addAll(workplaceRepository.findAllById(form.getWorkplaceIds()));
        }
        for (int i = 0; i < form.getItemLabels().size(); i++) {
            String label = TodoTaskForm.at(form.getItemLabels(), i);
            if (label == null || label.isBlank()) continue;
            TodoItemType type = TodoItemType.valueOf(TodoTaskForm.at(form.getItemTypes(), i));
            String options = TodoTaskForm.at(form.getItemOptions(), i);
            if (type == TodoItemType.CHOICE && (options == null || options.isBlank())) {
                throw new BusinessException("객관식 항목 '" + label + "' 의 선택지를 쉼표로 입력해 주세요.");
            }
            task.addItem(TodoItem.builder()
                    .type(type)
                    .label(label.strip())
                    .options(type == TodoItemType.CHOICE ? options : null)
                    .required("Y".equals(TodoTaskForm.at(form.getItemRequired(), i)))
                    .build());
        }
        if (task.getItems().isEmpty()) {
            throw new BusinessException("항목을 하나 이상 넣어 주세요.");
        }
        if (guideFiles != null) {
            for (MultipartFile f : guideFiles) {
                FileRef ref = fileStore.storeAttachment(f);    // DB 저장이 실패하면 FileStore 가 파일을 지운다(보상)
                if (ref != null) task.getGuideFiles().add(ref);
            }
        }
        todoTaskRepository.save(task);

        // 오늘이 해당하는 날이면 바로 오늘 몫을 만든다 (배치를 기다리지 않게)
        generator.generateFor(task, LocalDate.now());

        // 대상자에게 "새 할 일" 알림 (같은 트랜잭션에 Outbox 행 저장)
        List<Member> targets = new ArrayList<>(generator.targets(task));
        outboxService.notify(targets, NotificationType.TODO, "새 할 일 : " + task.getTitle(),
                task.getCycleText() + " 해야 하는 할 일이 배정됐어요.", "/todo", "TodoTask", task.getId(),
                "task-created:" + task.getId());
        return task;
    }

    public void toggleActive(Long id) {
        TodoTask task = get(id);
        task.setActive(!task.isActive());
    }

    // ---------- 수정 ----------
    //  항목 : itemIds[i] 가 있으면 기존 항목 고치기(종류는 그대로), 없으면 새 항목.
    //         폼에서 빠진 기존 항목은 낸 답이 있으면 숨기고(archived), 없으면 지운다 → 지난 제출 기록은 그대로 남는다.
    //  대상이 바뀌어 오늘 몫이 빠진 직원이 생기면 바로 배정한다 (이미 있는 배정은 건드리지 않음)
    public TodoTask update(Long id, TodoTaskForm form, List<MultipartFile> guideFiles) {
        validate(form);
        TodoTask task = get(id);
        task.setTitle(form.getTitle().strip());
        task.setGuide(form.getGuide());
        task.setCycle(form.getCycle());
        task.setDaysOfWeek(form.getCycle() == RepeatCycle.WEEKLY ? RecurrenceRule.joinDays(form.getDays()) : null);
        task.setDayOfMonth(form.getCycle() == RepeatCycle.MONTHLY ? form.getDayOfMonth() : null);
        task.setStartDate(form.getStartDate());
        task.setEndDate(form.getEndDate());
        task.setReviewer(form.getReviewerId() == null ? null : memberService.getMember(form.getReviewerId()));
        task.getAssignees().clear();
        task.getAssignees().addAll(memberService.getMembers(form.getMemberIds()));
        task.getWorkplaces().clear();
        if (form.getWorkplaceIds() != null && !form.getWorkplaceIds().isEmpty()) {
            task.getWorkplaces().addAll(workplaceRepository.findAllById(form.getWorkplaceIds()));
        }

        Map<Long, TodoItem> existing = new HashMap<>();
        task.getItems().forEach(it -> existing.put(it.getId(), it));
        Set<Long> kept = new HashSet<>();
        List<TodoItem> ordered = new ArrayList<>();
        for (int i = 0; i < form.getItemLabels().size(); i++) {
            String label = TodoTaskForm.at(form.getItemLabels(), i);
            if (label == null || label.isBlank()) continue;
            Long itemId = form.itemIdAt(i);
            TodoItem item = itemId == null ? null : existing.get(itemId);
            TodoItemType type = item != null ? item.getType() : TodoItemType.valueOf(TodoTaskForm.at(form.getItemTypes(), i));
            String options = TodoTaskForm.at(form.getItemOptions(), i);
            if (type == TodoItemType.CHOICE && (options == null || options.isBlank())) {
                throw new BusinessException("객관식 항목 '" + label + "' 의 선택지를 쉼표로 입력해 주세요.");
            }
            if (item == null) {
                item = TodoItem.builder().type(type).build();
                task.addItem(item);
            } else {
                kept.add(item.getId());
            }
            item.setLabel(label.strip());
            item.setOptions(type == TodoItemType.CHOICE ? options : null);
            item.setRequired("Y".equals(TodoTaskForm.at(form.getItemRequired(), i)));
            item.setArchived(false);
            ordered.add(item);
        }
        if (ordered.isEmpty()) {
            throw new BusinessException("항목을 하나 이상 넣어 주세요.");
        }
        for (TodoItem old : existing.values()) {
            if (kept.contains(old.getId())) continue;
            if (todoAnswerRepository.existsByItemId(old.getId())) {
                old.setArchived(true);              // 지난 답이 있으면 숨기기만
            } else {
                task.getItems().remove(old);        // orphanRemoval 로 행 삭제
            }
        }
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setSortOrder(i);
        int tail = ordered.size();
        for (TodoItem it : task.getItems()) {
            if (it.isArchived()) it.setSortOrder(tail++);
        }

        if (form.getRemoveGuideFiles() != null && !form.getRemoveGuideFiles().isEmpty()) {
            task.getGuideFiles().removeIf(f -> form.getRemoveGuideFiles().contains(f.getPath()));
        }
        if (guideFiles != null) {
            for (MultipartFile f : guideFiles) {
                FileRef ref = fileStore.storeAttachment(f);
                if (ref != null) task.getGuideFiles().add(ref);
            }
        }
        todoTaskRepository.flush();
        generator.generateFor(task, LocalDate.now());
        return task;
    }

    // ---------- 삭제 ----------
    //  지난 기록(제출·검수·댓글)은 남기고, 목록·자동 배정에서 뺀다.
    //  오늘부터 아직 제출하지 않은 배정은 함께 지운다 (직원의 오늘 할 일에서 사라진다)
    public void delete(Long id) {
        TodoTask task = get(id);
        task.setDeleted(true);
        task.setActive(false);
        List<TodoAssignment> upcoming = todoAssignmentRepository.findByTaskIdAndStatusAndWorkDateGreaterThanEqual(
                task.getId(), AssignmentStatus.NOT_SUBMITTED, LocalDate.now());
        for (TodoAssignment a : upcoming) {
            todoCommentRepository.deleteAll(todoCommentRepository.findByAssignmentIdOrderByIdAsc(a.getId()));
        }
        todoAssignmentRepository.deleteAll(upcoming);
    }

    // ---------- 할 일 단위 댓글 (@멘션) ----------

    @Transactional(readOnly = true)
    public List<TodoTaskComment> getComments(Long taskId) {
        return todoTaskCommentRepository.findByTaskIdOrderByIdAsc(taskId);
    }

    public void addComment(Long taskId, String username, String content) {
        if (content == null || content.isBlank()) return;
        Member me = memberService.getMember(username);
        TodoTask task = get(taskId);
        todoTaskCommentRepository.save(TodoTaskComment.builder()
                .task(task).author(me).content(content.strip()).created(LocalDateTime.now()).build());

        // 멘션 받은 사람에게 알림 : 관리자는 할 일 상세로, 직원은 내 할 일로
        List<Member> mentioned = memberService.findMentioned(content).stream()
                .filter(m -> !m.getId().equals(me.getId())).toList();
        List<Member> admins = mentioned.stream().filter(Member::isAdmin).toList();
        List<Member> others = mentioned.stream().filter(m -> !m.isAdmin()).toList();
        String title = me.getDisplayName() + " 님이 회원님을 언급했어요";
        String message = task.getTitle() + " : " + content.strip();
        outboxService.notify(admins, NotificationType.MENTION, title, message, "/admin/todo/" + task.getId(), "TodoTask", task.getId(), null);
        outboxService.notify(others, NotificationType.MENTION, title, message, "/todo", "TodoTask", task.getId(), null);
    }

    private void validate(TodoTaskForm form) {
        if (form.getCycle() == RepeatCycle.WEEKLY && (form.getDays() == null || form.getDays().isEmpty())) {
            throw new BusinessException("매주 반복이면 요일을 하나 이상 골라 주세요.");
        }
        if (form.getCycle() == RepeatCycle.MONTHLY
                && (form.getDayOfMonth() == null || form.getDayOfMonth() < 1 || form.getDayOfMonth() > 31)) {
            throw new BusinessException("매월 반복이면 날짜(1~31)를 입력해 주세요.");
        }
        if (form.getEndDate() != null && form.getEndDate().isBefore(form.getStartDate())) {
            throw new BusinessException("종료일이 시작일보다 빨라요.");
        }
        if ((form.getMemberIds() == null || form.getMemberIds().isEmpty())
                && (form.getWorkplaceIds() == null || form.getWorkplaceIds().isEmpty())) {
            throw new BusinessException("대상 직원이나 근무지를 하나 이상 골라 주세요.");
        }
    }
}
