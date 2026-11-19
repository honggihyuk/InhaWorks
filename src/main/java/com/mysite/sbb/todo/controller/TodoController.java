package com.mysite.sbb.todo.controller;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.common.dto.AnswerInput;
import com.mysite.sbb.todo.entity.TodoAssignment;
import com.mysite.sbb.todo.entity.TodoItem;
import com.mysite.sbb.todo.service.TodoAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

// 직원 : 할 일 (오늘 목록 → 채워서 제출), 검수자 : 검수 대기함 → 통과 / 재작업 요청
@Controller
@RequiredArgsConstructor
@RequestMapping("/todo")
public class TodoController {

    private final MemberService memberService;
    private final TodoAssignmentService todoAssignmentService;

    @GetMapping
    public String list(@RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        LocalDate today = LocalDate.now();
        LocalDate d = date == null ? today : date;
        model.addAttribute("date", d);
        model.addAttribute("today", today);
        model.addAttribute("assignments", todoAssignmentService.getMine(me, d));
        model.addAttribute("overdue", todoAssignmentService.getMyOverdue(me, today));
        model.addAttribute("reviewWaiting", me.isAdmin() ? todoAssignmentService.getReviewQueue(me).size()
                : todoAssignmentService.countReviewWaiting(me));
        return "todo/list";
    }

    @GetMapping("/review")
    public String reviewQueue(Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        model.addAttribute("queue", todoAssignmentService.getReviewQueue(me));
        return "todo/review";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Principal principal, Model model) {
        Member me = memberService.getMember(principal.getName());
        TodoAssignment a = todoAssignmentService.getForView(id, me);
        model.addAttribute("a", a);
        model.addAttribute("canSubmit", todoAssignmentService.canSubmit(a, me));
        model.addAttribute("canReview", todoAssignmentService.canReview(a, me));
        model.addAttribute("canUndoReview", todoAssignmentService.canUndoReview(a, me));
        model.addAttribute("comments", todoAssignmentService.getComments(id));
        model.addAttribute("submitToken", UUID.randomUUID().toString());   // 중복 제출 방지 토큰
        return "todo/detail";
    }

    // 항목별 입력 이름 : item_{항목ID} (글자 값, 체크박스는 여러 개), file_{항목ID} (사진), sig_{항목ID} (서명 data URL)
    @PostMapping("/{id}/submit")
    public String submit(@PathVariable("id") Long id, MultipartHttpServletRequest request,
                         Principal principal, RedirectAttributes ra) {
        Member me = memberService.getMember(principal.getName());
        TodoAssignment a = todoAssignmentService.getForView(id, me);
        Map<Long, AnswerInput> inputs = new HashMap<>();
        for (TodoItem item : a.getTask().getActiveItems()) {
            String[] values = request.getParameterValues("item_" + item.getId());
            inputs.put(item.getId(), new AnswerInput(values == null ? List.of() : Arrays.asList(values),
                    request.getFile("file_" + item.getId()), request.getParameter("sig_" + item.getId())));
        }
        todoAssignmentService.submit(id, principal.getName(), inputs, request.getParameter("submitToken"));
        ra.addFlashAttribute("todoMessage", "제출했어요.");
        return "redirect:/todo/" + id;
    }

    @PostMapping("/{id}/review")
    public String review(@PathVariable("id") Long id,
                         @RequestParam("decision") String decision,
                         @RequestParam(value = "reason", required = false) String reason,
                         Principal principal, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        todoAssignmentService.review(id, principal.getName(), approve, reason);
        ra.addFlashAttribute("todoMessage", approve ? "검수 통과로 처리했어요." : "재작업을 요청했어요.");
        if (approve) {
            ra.addFlashAttribute("undoUrl", "/todo/" + id + "/review/undo");   // 토스트의 '실행 취소'
        }
        return "redirect:/todo/" + id;
    }

    // 검수 통과 실행 취소 (검수한 사람 · 10분 안)
    @PostMapping("/{id}/review/undo")
    public String undoReview(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        todoAssignmentService.undoReview(id, principal.getName());
        ra.addFlashAttribute("todoMessage", "검수 통과를 취소했어요. 다시 검수 대기중이에요.");
        return "redirect:/todo/" + id;
    }

    @PostMapping("/{id}/comment")
    public String comment(@PathVariable("id") Long id, @RequestParam("content") String content, Principal principal) {
        todoAssignmentService.addComment(id, principal.getName(), content);
        return "redirect:/todo/" + id + "#comments";
    }
}
