package com.mysite.sbb.chat.repository;

import com.mysite.sbb.chat.entity.ChatMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMentionRepository extends JpaRepository<ChatMention, Long> {

    @Query("select c from ChatMention c join fetch c.member where c.message.id in :messageIds")
    List<ChatMention> findByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
