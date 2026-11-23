package com.mysite.sbb.board.controller;

import com.mysite.sbb.board.constant.IssueCategory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.IssueFilter;
import com.mysite.sbb.board.dto.IssueStatusForm;
import com.mysite.sbb.board.dto.PostForm;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.LocalDate;
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
    private final FileStore fileStore;

    @Value("${app.security.bulk-download-enabled:true}")
    private boolean bulkDownloadEnabled;

    @GetMapping
    public String boards(Principal principal, Model model) {
        Member me = me(principal);
        Map<Board, Long> boards = new LinkedHashMap<>();
        boardService.getBoards(me).forEach(b -> boards.put(b, boardService.countPosts(b.getId())));
        model.addAttribute("boards", boards);
        model.addAttribute("pinned", boardService.getPinned(me));
        return "board/index";
    }

    // 이슈 & 해결 게시판은 지점(place) · 분류(category) · 상태(status) · 기간(from, to) 필터와 통계를 함께 보여 준다
    @GetMapping("/{id}")
    public String posts(@PathVariable("id") Long id, @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "place", required = false) Long place,
                        @RequestParam(value = "category", required = false) IssueCategory category,
                        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Principal principal, Model model) {
        Member me = me(principal);
        Board board = boardService.getBoard(id, me);
        IssueFilter filter = new IssueFilter(status, place, category, from, to);
        model.addAttribute("board", board);
        if (board.isIssueBoard()) {
            model.addAttribute("posts", boardService.getIssuePosts(board, filter));
            model.addAttribute("summary", boardService.issueSummary(board, place));
            model.addAttribute("workplaces", organizationService.getWorkplaces());
            model.addAttribute("categories", IssueCategory.values());
            model.addAttribute("place", place);
            model.addAttribute("category", category);
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("bulkDownloadEnabled", bulkDownloadEnabled);
            Map<IssueStatus, Long> stats = boardService.issueStats(board);
            long total = stats.values().stream().mapToLong(Long::longValue).sum();
            model.addAttribute("stats", stats);
            model.addAttribute("resolvedRate", total == 0 ? 0 : Math.round(stats.get(IssueStatus.RESOLVED) * 100.0 / total));
        } else {
            model.addAttribute("posts", boardService.getPosts(board, null));
        }
        model.addAttribute("status", filter.status());
        model.addAttribute("issueStatuses", IssueStatus.values());
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
        if (post.getBoard().isIssueBoard()) model.addAttribute("history", boardService.getHistory(id));
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
    public String issue(@PathVariable("id") Long id, @RequestParam("status") String status,
                        @RequestParam(value = "resolution", required = false) String resolution, Principal principal) {
        IssueStatus parsed = IssueStatus.parse(status);     // 예전 값 OPEN 도 받는다
        if (parsed == null) throw new IllegalArgumentException("상태를 골라 주세요.");
        boardService.changeIssue(id, me(principal), parsed, resolution);
        return "redirect:/board/post/" + id;
    }

    // 상태 칩 메뉴 (관리자) : status, memo, (해결 완료면) resolution
    @PostMapping("/post/{id}/issue/status")
    public String issueStatus(@PathVariable("id") Long id, @Valid IssueStatusForm form, BindingResult bindingResult,
                              Principal principal, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/board/post/" + id;
        }
        try {
            boardService.changeStatus(id, me(principal), form.getStatus(), form.getMemo(), form.getResolution());
            ra.addFlashAttribute("attendanceMessage", "'" + form.getStatus().getLabel() + "' 로 바꿨어요.");
        } catch (NoAccessException e) {
            throw e;
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/board/post/" + id;
    }

    // 첨부 내려받기 : 글을 볼 권한을 확인한 뒤에만 (SEC-05)
    @GetMapping("/post/{id}/files/{name}")
    public ResponseEntity<Resource> file(@PathVariable("id") Long id, @PathVariable("name") String name, Principal principal) {
        FileRef ref = boardService.getPostFile(id, name, me(principal));
        Path path = fileStore.resolve(ref.getPath());
        if (path == null || !Files.isRegularFile(path)) throw new EntityNotFoundException("파일을 찾을 수 없어요.");
        String filename = ref.getOriginalName() == null ? name : ref.getOriginalName();
        MediaType type = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition cd = (ref.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(ref.isImage() ? type : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
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
        ra.addFlashAttribute("attendanceMessage", "해결을 취소했어요. 다시 검토 중이에요.");
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
