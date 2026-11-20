package com.mysite.sbb.board.controller;

import com.mysite.sbb.board.constant.IssueCategory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.PostForm;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 게시판 : 게시판 목록 → 글 목록 (고정 공지 먼저, 이슈 게시판은 상태 통계) → 글 보기 / 쓰기 / 댓글
@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
public class BoardController {

    private final BoardService boardService;
    private final MemberService memberService;
    private final com.mysite.sbb.organization.service.OrganizationService organizationService;

    @GetMapping
    public String boards(Principal principal, Model model) {
        Member me = me(principal);
        Map<Board, Long> boards = new LinkedHashMap<>();
        boardService.getBoards(me).forEach(b -> boards.put(b, boardService.countPosts(b.getId())));
        model.addAttribute("boards", boards);
        model.addAttribute("pinned", boardService.getPinned(me));
        return "board/index";
    }

    // 이슈 & 해결 게시판은 지점(place) · 분류(category) · 상태(status) 필터와 통계를 함께 보여 준다
    @GetMapping("/{id}")
    public String posts(@PathVariable("id") Long id, @RequestParam(value = "status", required = false) IssueStatus status,
                        @RequestParam(value = "place", required = false) Long place,
                        @RequestParam(value = "category", required = false) IssueCategory category,
                        Principal principal, Model model) {
        Member me = me(principal);
        Board board = boardService.getBoard(id, me);
        model.addAttribute("board", board);
        if (board.isIssueBoard()) {
            model.addAttribute("posts", boardService.getIssuePosts(board, status, place, category));
            model.addAttribute("summary", boardService.issueSummary(board, place));
            model.addAttribute("workplaces", organizationService.getWorkplaces());
            model.addAttribute("categories", IssueCategory.values());
            model.addAttribute("place", place);
            model.addAttribute("category", category);
        } else {
            model.addAttribute("posts", boardService.getPosts(board, status));
        }
        model.addAttribute("status", status);
        model.addAttribute("issueStatuses", IssueStatus.values());
        if (board.isIssueBoard()) {
            Map<IssueStatus, Long> stats = boardService.issueStats(board);
            long total = stats.values().stream().mapToLong(Long::longValue).sum();
            model.addAttribute("stats", stats);
            model.addAttribute("resolvedRate", total == 0 ? 0 : Math.round(stats.get(IssueStatus.RESOLVED) * 100.0 / total));
        }
        return "board/list";
    }

    @ModelAttribute("issueCategories")
    public IssueCategory[] issueCategories() {
        return IssueCategory.values();
    }

    @ModelAttribute("issueWorkplaces")
    public java.util.List<com.mysite.sbb.organization.entity.Workplace> issueWorkplaces() {
        return organizationService.getWorkplaces();
    }

    @GetMapping("/{id}/write")
    public String writeForm(@PathVariable("id") Long id, PostForm postForm, Principal principal, Model model) {
        Member me = me(principal);
        if (postForm.getWorkplaceId() == null && me.getWorkplace() != null) postForm.setWorkplaceId(me.getWorkplace().getId());
        model.addAttribute("board", boardService.getBoard(id, me));
        model.addAttribute("postId", null);
        return "board/form";
    }

    @PostMapping("/{id}/write")
    public String write(@PathVariable("id") Long id, @Valid PostForm postForm, BindingResult bindingResult,
                        @RequestParam(value = "files", required = false) List<MultipartFile> files,
                        Principal principal, Model model) {
        Member me = me(principal);
        if (bindingResult.hasErrors()) {
            model.addAttribute("board", boardService.getBoard(id, me));
            model.addAttribute("postId", null);
            return "board/form";
        }
        Post post = boardService.write(id, me, postForm, files);
        return "redirect:/board/post/" + post.getId();
    }

    @GetMapping("/post/{id}")
    public String detail(@PathVariable("id") Long id, Principal principal, Model model) {
        Member me = me(principal);
        Post post = boardService.read(id, me);
        model.addAttribute("post", post);
        model.addAttribute("canEdit", boardService.canEdit(post, me));
        model.addAttribute("canReopen", boardService.canReopen(post, me));
        model.addAttribute("comments", boardService.getComments(id));
        model.addAttribute("issueStatuses", IssueStatus.values());
        return "board/detail";
    }

    @GetMapping("/post/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Principal principal, Model model) {
        Post post = boardService.getPost(id, me(principal));
        model.addAttribute("postForm", PostForm.of(post));
        model.addAttribute("board", post.getBoard());
        model.addAttribute("postId", id);
        return "board/form";
    }

    @PostMapping("/post/{id}/edit")
    public String edit(@PathVariable("id") Long id, @Valid PostForm postForm, BindingResult bindingResult,
                       @RequestParam(value = "files", required = false) List<MultipartFile> files,
                       Principal principal, Model model) {
        Member me = me(principal);
        if (bindingResult.hasErrors()) {
            model.addAttribute("board", boardService.getPost(id, me).getBoard());
            model.addAttribute("postId", id);
            return "board/form";
        }
        boardService.update(id, me, postForm, files);
        return "redirect:/board/post/" + id;
    }

    @PostMapping("/post/{id}/delete")
    public String delete(@PathVariable("id") Long id, Principal principal) {
        Long boardId = boardService.delete(id, me(principal));
        return "redirect:/board/" + boardId;
    }

    @PostMapping("/post/{id}/pin")
    public String pin(@PathVariable("id") Long id, Principal principal) {
        boardService.togglePin(id, me(principal));
        return "redirect:/board/post/" + id;
    }

    @PostMapping("/post/{id}/issue")
    public String issue(@PathVariable("id") Long id, @RequestParam("status") IssueStatus status,
                        @RequestParam(value = "resolution", required = false) String resolution, Principal principal) {
        boardService.changeIssue(id, me(principal), status, resolution);
        return "redirect:/board/post/" + id;
    }

    @PostMapping("/post/{id}/issue/start")
    public String startIssue(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        boardService.startIssue(id, me(principal));
        ra.addFlashAttribute("attendanceMessage", "처리를 시작했어요.");
        return "redirect:/board/post/" + id;
    }

    @PostMapping("/post/{id}/issue/resolve")
    public String resolveIssue(@PathVariable("id") Long id, @RequestParam(value = "resolution", required = false) String resolution,
                               @RequestParam(value = "files", required = false) List<MultipartFile> files,
                               Principal principal, RedirectAttributes ra) {
        try {
            boardService.resolveIssue(id, me(principal), resolution, files);
            ra.addFlashAttribute("attendanceMessage", "해결을 등록했어요.");
        } catch (com.mysite.sbb.common.exception.BusinessException | IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/board/post/" + id;
    }

    @PostMapping("/post/{id}/issue/reopen")
    public String reopenIssue(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        boardService.reopenIssue(id, me(principal));
        ra.addFlashAttribute("attendanceMessage", "해결을 취소했어요. 다시 처리 대기예요.");
        return "redirect:/board/post/" + id;
    }

    @PostMapping("/post/{id}/comment")
    public String comment(@PathVariable("id") Long id, @RequestParam("content") String content, Principal principal) {
        boardService.addComment(id, me(principal), content);
        return "redirect:/board/post/" + id + "#comments";
    }

    private Member me(Principal principal) {
        return memberService.getMember(principal.getName());
    }
}
