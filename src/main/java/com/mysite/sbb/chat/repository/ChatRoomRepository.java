package com.mysite.sbb.chat.repository;

import com.mysite.sbb.chat.constant.ChatRoomType;
import com.mysite.sbb.chat.entity.ChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByChannelKey(String channelKey);

    Optional<ChatRoom> findByDirectKey(String directKey);

    List<ChatRoom> findByType(ChatRoomType type);

    List<ChatRoom> findByInquiryTopicId(Long topicId);

    // [신뢰성] 방 단위로 전송을 줄 세운다 : 메시지 id 순서 = 커밋 순서 → afterId 보정에서 빠지는 메시지가 없다 (REL-22)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findByIdForUpdate(@Param("id") Long id);

    @Query("select r from ChatRoom r left join fetch r.inquiryTopic left join fetch r.inquiryMember "
            + "where r.type = com.mysite.sbb.chat.constant.ChatRoomType.INQUIRY order by r.lastMessageAt desc nulls last, r.id desc")
    List<ChatRoom> findInquiries();

    @Query("select r from ChatRoom r where r.archived = false and r.type <> com.mysite.sbb.chat.constant.ChatRoomType.DIRECT "
            + "order by r.type asc, r.id asc")
    List<ChatRoom> findChannels();

    long countByType(ChatRoomType type);
}
