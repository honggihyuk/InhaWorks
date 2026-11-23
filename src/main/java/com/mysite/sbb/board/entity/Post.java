package com.mysite.sbb.board.entity;

import com.mysite.sbb.board.constant.IssueCategory;
import com.mysite.sbb.organization.entity.Workplace;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 게시글. 이슈&해결 게시판이면 issueStatus / 분류 / 지점 / 해결(내용·첨부·해결자·시각)을 쓴다
@Entity
@Table(indexes = @Index(name = "idx_post_board", columnList = "board_id, pinned"))
@DynamicUpdate      // 바뀐 칸만 UPDATE : 조회수 +1 이 동시에 바뀐 이슈 상태를 예전 값으로 덮어쓰지 않게
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

    // VARCHAR 로 고정 : MySQL 기본 매핑(ENUM)이면 단계를 늘릴 때 저장이 실패한다 (IssueStatusMigration 참고)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private IssueStatus issueStatus;

    @Column(columnDefinition = "TEXT")
    private String resolution;              // 해결 방법

    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member resolvedBy;              // 해결한 사람

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IssueCategory category;         // 이슈 분류 (재고, 시설 ...)

    @ManyToOne(fetch = FetchType.LAZY)
    private Workplace workplace;            // 이슈가 생긴 지점 (매장)

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "post_resolution_file", joinColumns = @JoinColumn(name = "post_id"),
            indexes = @Index(name = "idx_post_resolution_file_path", columnList = "path"))
    private List<FileRef> resolutionFiles = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "post_file", joinColumns = @JoinColumn(name = "post_id"),
            indexes = @Index(name = "idx_post_file_path", columnList = "path"))
    private List<FileRef> files = new ArrayList<>();

    private int viewCount;

    // [신뢰성] 중복 등록 방지 (글쓰기 화면을 열 때 만든 UUID)
    @Column(unique = true, length = 36)
    private String submitToken;

    private LocalDateTime created;

    private LocalDateTime updated;

    // 경과 일수 : 해결 전에는 지금까지, 해결했으면 해결까지 (소수 한 자리)
    public double getElapsedDays() {
        if (created == null) return 0;
        LocalDateTime end = resolvedAt != null && issueStatus == IssueStatus.RESOLVED ? resolvedAt : LocalDateTime.now();
        return Math.round(java.time.Duration.between(created, end).toMinutes() / 144.0) / 10.0;
    }

    public String getElapsedText() {
        double d = getElapsedDays();
        boolean done = issueStatus == IssueStatus.RESOLVED;
        if (d < 1) return done ? "당일 해결" : "오늘 등록";
        String n = d == Math.floor(d) ? (long) d + "일" : d + "일";
        return done ? n + " 만에 해결" : n + "째";
    }
}
