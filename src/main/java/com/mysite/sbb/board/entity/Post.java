package com.mysite.sbb.board.entity;

import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 게시글. 이슈&해결 게시판이면 issueStatus / resolution 을 쓴다
@Entity
@Table(indexes = @Index(name = "idx_post_board", columnList = "board_id, pinned"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member author;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean pinned;                 // 상단 고정 공지 (관리자)

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IssueStatus issueStatus;

    @Column(columnDefinition = "TEXT")
    private String resolution;              // 해결 방법

    private LocalDateTime resolvedAt;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "post_file", joinColumns = @JoinColumn(name = "post_id"))
    private List<FileRef> files = new ArrayList<>();

    private int viewCount;

    // [신뢰성] 중복 등록 방지 (글쓰기 화면을 열 때 만든 UUID)
    @Column(unique = true, length = 36)
    private String submitToken;

    private LocalDateTime created;

    private LocalDateTime updated;
}
