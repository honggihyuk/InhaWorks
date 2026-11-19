package com.mysite.sbb.board.controller;

import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.service.BoardService;
import com.mysite.sbb.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// 관리자 : 게시판 만들기 / 지우기 (일반, 이슈&해결, 주제별, 팀 게시판)
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/board")
public class AdminBoardController {

    private final BoardService boardService;
    private final OrganizationService organizationService;

    @GetMapping
    public String list(Model model) {
        Map<Board, Long> boards = new LinkedHashMap<>();
        boardService.getAllBoards().forEach(b -> boards.put(b, boardService.countPosts(b.getId())));
        model.addAttribute("boards", boards);
        model.addAttribute("types", BoardType.values());
        model.addAttribute("teams", organizationService.getTeams());
        return "admin/board";
    }

    @PostMapping
    public String create(@RequestParam("name") String name,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam("type") BoardType type,
                         @RequestParam(value = "teamId", required = false) Long teamId) {
        boardService.createBoard(name, description, type, teamId);
        return "redirect:/admin/board";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        boardService.deleteBoard(id);
        return "redirect:/admin/board";
    }
}
