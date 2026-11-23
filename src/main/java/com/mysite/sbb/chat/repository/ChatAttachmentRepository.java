package com.mysite.sbb.chat.repository;

import com.mysite.sbb.chat.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    List<ChatAttachment> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

    @Query("select a from ChatAttachment a join fetch a.message m join fetch m.room where a.id = :id")
    Optional<ChatAttachment> findWithRoom(@Param("id") Long id);
}
