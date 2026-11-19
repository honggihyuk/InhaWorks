package com.mysite.sbb.board.entity;

import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.organization.entity.Team;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 게시판 (관리자가 만든다) : 일반 / 이슈&해결, 주제별 게시판, 팀 게시판(team 을 고르면 그 팀만 볼 수 있다)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType type;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;                      // 팀 게시판이면 그 팀 (null 이면 전 직원)

    private int sortOrder;

    private LocalDateTime created;

    public boolean isIssueBoard() {
        return type == BoardType.ISSUE;
    }

    public boolean canAccess(Member m) {
        return team == null || m.isAdmin() || (m.getTeam() != null && m.getTeam().getId().equals(team.getId()));
    }
}
