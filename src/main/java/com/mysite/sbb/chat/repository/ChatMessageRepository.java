package com.mysite.sbb.chat.repository;

import com.mysite.sbb.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);

    long countByRoomId(Long roomId);

    @Query("select m from ChatMessage m left join fetch m.sender s left join fetch s.workPolicy where m.room.id = :roomId order by m.id desc")
    List<ChatMessage> findLatest(@Param("roomId") Long roomId, Pageable pageable);

    @Query("select m from ChatMessage m left join fetch m.sender s left join fetch s.workPolicy where m.room.id = :roomId and m.id < :beforeId order by m.id desc")
    List<ChatMessage> findBefore(@Param("roomId") Long roomId, @Param("beforeId") long beforeId, Pageable pageable);

    @Query("select m from ChatMessage m left join fetch m.sender s left join fetch s.workPolicy where m.room.id = :roomId and m.id > :afterId order by m.id asc")
    List<ChatMessage> findAfter(@Param("roomId") Long roomId, @Param("afterId") long afterId, Pageable pageable);

    @Query("select m.room.id, count(m) from ChatMessage m where m.room.id in :roomIds group by m.room.id")
    List<Object[]> countByRooms(@Param("roomIds") Collection<Long> roomIds);

    @Query("select count(m) from ChatMessage m where m.room.type = com.mysite.sbb.chat.constant.ChatRoomType.DIRECT")
    long countDirectMessages();
}
