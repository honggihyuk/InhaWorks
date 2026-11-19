package com.mysite.sbb.board.controller;

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

    @GetMapping
    public String boards(Principal principal, Model model) {
        Member me = me(principal);
        Map<Board, Long> boards = new LinkedHashMap<>();
        boardService.getBoards(me).forEach(b -> boards.put(b, boardService.countPosts(b.getId())));
        model.addAttribute("boards", boards);
        model.addAttribute("pinned", boardService.getPinned(me));
        return "board/index";
    }

    @GetMapping("/{id}")
    public String posts(@PathVariable("id") Long id, @RequestParam(value = "status", required = false) IssueStatus status,
                        Principal principal, Model model) {
        Member me = me(principal);
        Board board = boardService.getBoard(id, me);
        model.addAttribute("board", board);
        model.addAttribute("posts", boardService.getPosts(board, status));
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

    @GetMapping("/{id}/write")
    public String writeForm(@PathVariable("id") Long id, PostForm postForm, Principal principal, Model model) {
        model.addAttribute("board", boardService.getBoard(id, me(principal)));
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

    @PostMapping("/post/{id}/comment")
    public String comment(@PathVariable("id") Long id, @RequestParam("content") String content, Principal principal) {
        boardService.addComment(id, me(principal), content);
        return "redirect:/board/post/" + id + "#comments";
    }

    private Member me(Principal principal) {
        return memberService.getMember(principal.getName());
    }
}
