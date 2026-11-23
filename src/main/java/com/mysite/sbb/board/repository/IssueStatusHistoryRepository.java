package com.mysite.sbb.board.repository;

import com.mysite.sbb.board.entity.IssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssueStatusHistoryRepository extends JpaRepository<IssueStatusHistory, Long> {

    @Query("select h from IssueStatusHistory h left join fetch h.changedBy where h.post.id = :postId order by h.id asc")
    List<IssueStatusHistory> findTimeline(@Param("postId") Long postId);

    @Query("select h from IssueStatusHistory h left join fetch h.changedBy where h.post.id in :postIds order by h.id asc")
    List<IssueStatusHistory> findTimelines(@Param("postIds") Collection<Long> postIds);

    void deleteByPostId(Long postId);
}
