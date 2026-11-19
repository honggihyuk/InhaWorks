package com.mysite.sbb.board.repository;

import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 고정 글 먼저, 그다음 최신순
    List<Post> findByBoardIdOrderByPinnedDescIdDesc(Long boardId);

    List<Post> findByBoardIdAndIssueStatusOrderByPinnedDescIdDesc(Long boardId, IssueStatus status);

    long countByBoardId(Long boardId);

    long countByBoardIdAndIssueStatus(Long boardId, IssueStatus status);

    List<Post> findTop20ByOrderByIdDesc();

    List<Post> findTop5ByPinnedTrueOrderByIdDesc();

    Optional<Post> findBySubmitToken(String submitToken);
}
