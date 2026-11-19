package com.mysite.sbb.board.repository;

import com.mysite.sbb.board.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findByPostIdOrderByIdAsc(Long postId);

    long countByPostId(Long postId);

    @Modifying
    @Query("delete from PostComment c where c.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
