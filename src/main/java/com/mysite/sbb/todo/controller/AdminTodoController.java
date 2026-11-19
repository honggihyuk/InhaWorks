package com.mysite.sbb.todo.controller;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import com.mysite.sbb.todo.constant.TodoItemType;
import com.mysite.sbb.todo.dto.TodoTaskForm;
import com.mysite.sbb.todo.entity.TodoTask;
import com.mysite.sbb.todo.service.TodoAssignmentService;
import com.mysite.sbb.todo.service.TodoTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 관리자 : 할 일 만들기, 목록(오늘 완료율), 대시보드(직원별 상태), 푸시
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/todo")
public class AdminTodoController {

    private final TodoTaskService todoTaskService;
    private final TodoAssignmentService todoAssignmentService;
    private final MemberService memberService;
    private final OrganizationService organizationService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("summaries", todoTaskService.getSummaries(LocalDate.now()));
        return "admin/todo-list";
    }

    @GetMapping("/new")
    public String createForm(TodoTaskForm todoTaskForm, Model model) {
        return form(model);
    }

    @PostMapping("/new")
    public String create(@Valid TodoTaskForm todoTaskForm, BindingResult bindingResult,
                         @RequestParam(value = "guideFiles", required = false) List<MultipartFile> guideFiles,
                         Principal principal, Model model, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return form(model);
        }
        try {
            TodoTask task = todoTaskService.create(todoTaskForm, guideFiles, principal.getName());
            ra.addFlashAttribute("adminMessage", "할 일 '" + task.getTitle() + "' 을 만들었어요.");
            return "redirect:/admin/todo/" + task.getId();
        } catch (BusinessException | IllegalArgumentException e) {
            bindingResult.reject("invalid", e.getMessage());
            return form(model);
        }
    }

    // 대시보드 : 날짜별 완료율 + 직원별 상태
    @GetMapping("/{id}")
    public String dashboard(@PathVariable("id") Long id,
                            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model) {
        TodoTask task = todoTaskService.get(id);
        model.addAttribute("board", todoAssignmentService.dashboard(task, date == null ? LocalDate.now() : date));
        model.addAttribute("remindToken", UUID.randomUUID().toString());   // 푸시 더블클릭 방지 (멱등성 키)
        model.addAttribute("taskComments", todoTaskService.getComments(id));
        return "admin/todo-dashboard";
    }

    // ---------- 수정 ----------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        TodoTask task = todoTaskService.get(id);
        model.addAttribute("todoTaskForm", TodoTaskForm.of(task));
        model.addAttribute("task", task);
        return form(model);
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id, @Valid TodoTaskForm todoTaskForm, BindingResult bindingResult,
                       @RequestParam(value = "guideFiles", required = false) List<MultipartFile> guideFiles,
                       Model model, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("task", todoTaskService.get(id));
            return form(model);
        }
        try {
            TodoTask task = todoTaskService.update(id, todoTaskForm, guideFiles);
            ra.addFlashAttribute("adminMessage", "할 일 '" + task.getTitle() + "' 을 수정했어요.");
            return "redirect:/admin/todo/" + id;
        } catch (BusinessException | IllegalArgumentException e) {
            bindingResult.reject("invalid", e.getMessage());
            model.addAttribute("task", todoTaskService.get(id));
            return form(model);
        }
    }

    // ---------- 삭제 (지난 기록은 남기고 목록·자동 배정에서 뺀다) ----------

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        String title = todoTaskService.get(id).getTitle();
        todoTaskService.delete(id);
        ra.addFlashAttribute("adminMessage", "할 일 '" + title + "' 을 삭제했어요.");
        return "redirect:/admin/todo";
    }

    // ---------- 할 일 단위 댓글 ----------

    @PostMapping("/{id}/comment")
    public String comment(@PathVariable("id") Long id, @RequestParam("content") String content, Principal principal) {
        todoTaskService.addComment(id, principal.getName(), content);
        return "redirect:/admin/todo/" + id + "#comments";
    }

    // '푸시' : 미완료 직원에게만 다시 알림
    @PostMapping("/{id}/remind")
    public String remind(@PathVariable("id") Long id,
                         @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam("token") String token, RedirectAttributes ra) {
        try {
            int sent = todoAssignmentService.remind(id, date, token);
            ra.addFlashAttribute("adminMessage", sent == 0 ? "새로 보낼 대상이 없어요. (이미 보냈거나 모두 완료)"
                    : "미완료 직원 " + sent + "명에게 다시 알림을 보냈어요.");
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러 같은 멱등성 키가 겹친 경우 : 먼저 들어간 요청이 이미 보냈다
            ra.addFlashAttribute("adminMessage", "이미 보낸 알림이에요.");
        }
        return "redirect:/admin/todo/" + id + "?date=" + date;
    }

    @PostMapping("/{id}/active")
    public String toggleActive(@PathVariable("id") Long id) {
        todoTaskService.toggleActive(id);
        return "redirect:/admin/todo";
    }

    private String form(Model model) {
        model.addAttribute("members", memberService.getActiveMembers());
        model.addAttribute("workplaces", organizationService.getWorkplaces());
        model.addAttribute("cycles", List.of(RepeatCycle.DAILY, RepeatCycle.WEEKLY, RepeatCycle.MONTHLY));
        model.addAttribute("weekDays", DayOfWeek.values());
        model.addAttribute("itemTypes", TodoItemType.values());
        return "admin/todo-form";
    }
}
