package com.mysite.sbb.board.repository;

import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    // 이슈 게시판 : 통계·필터는 서비스에서 (게시판 하나의 글 수가 많지 않다)
    java.util.List<Post> findByBoardIdOrderByIdDesc(Long boardId);

    // 고정 글 먼저, 그다음 최신순
    List<Post> findByBoardIdOrderByPinnedDescIdDesc(Long boardId);

    List<Post> findByBoardIdAndIssueStatusOrderByPinnedDescIdDesc(Long boardId, IssueStatus status);

    long countByBoardId(Long boardId);

    long countByBoardIdAndIssueStatus(Long boardId, IssueStatus status);

    List<Post> findTop20ByOrderByIdDesc();

    List<Post> findTop5ByPinnedTrueOrderByIdDesc();

    Optional<Post> findBySubmitToken(String submitToken);

    // 이슈 상태 변경 : 행을 잠그고 읽어서 두 사람이 동시에 바꿔도 이력이 어긋나지 않게 한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    // /uploads 직접 접근 검사 : 이 파일이 붙은 글
    @Query("select distinct p from Post p join fetch p.board b left join fetch b.team join p.files f where f.path = :path")
    List<Post> findByFilePath(@Param("path") String path);

    @Query("select distinct p from Post p join fetch p.board b left join fetch b.team join p.resolutionFiles f where f.path = :path")
    List<Post> findByResolutionFilePath(@Param("path") String path);
}
