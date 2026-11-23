package com.mysite.sbb.chat.repository;

import com.mysite.sbb.chat.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByRoomIdAndMemberId(Long roomId, Long memberId);

    List<ChatParticipant> findByMemberId(Long memberId);

    List<ChatParticipant> findByRoomId(Long roomId);

    long countByRoomIdAndLeftRoomFalse(Long roomId);

    boolean existsByRoomIdAndMemberIdAndLeftRoomFalse(Long roomId, Long memberId);

    @Query("select p from ChatParticipant p join fetch p.room r where p.member.id = :memberId and p.leftRoom = false and r.archived = false")
    List<ChatParticipant> findMine(@Param("memberId") Long memberId);

    @Query("select p from ChatParticipant p join fetch p.member m left join fetch m.team left join fetch m.workplace "
            + "left join fetch m.workPolicy where p.room.id = :roomId and p.leftRoom = false order by m.name asc")
    List<ChatParticipant> findActive(@Param("roomId") Long roomId);

    @Query("select p from ChatParticipant p join fetch p.member where p.room.id in :roomIds and p.leftRoom = false")
    List<ChatParticipant> findActiveInRooms(@Param("roomIds") Collection<Long> roomIds);

    // 내 방별 안 읽은 수 (내가 보낸 메시지는 빼고)
    @Query("select p.room.id, count(m) from ChatParticipant p, ChatMessage m where p.member.id = :memberId and p.leftRoom = false "
            + "and p.room.archived = false and m.room.id = p.room.id and m.id > p.lastReadMessageId "
            + "and (m.sender is null or m.sender.id <> :memberId) group by p.room.id")
    List<Object[]> countUnreadByRoom(@Param("memberId") Long memberId);

    // 사이드바 전체 안 읽은 수 (알림 끈 방은 뺀다) : 여러 직원을 한 번에
    @Query("select p.member.id, count(m) from ChatParticipant p, ChatMessage m where p.member.id in :memberIds and p.leftRoom = false "
            + "and p.muted = false and p.room.archived = false and m.room.id = p.room.id and m.id > p.lastReadMessageId "
            + "and (m.sender is null or m.sender.id <> p.member.id) group by p.member.id")
    List<Object[]> countUnreadTotals(@Param("memberIds") Collection<Long> memberIds);

    // [신뢰성] 읽음 위치는 앞으로만 간다 : 늦게 도착한 예전 요청이 되돌리지 못한다
    @Modifying
    @Query("update ChatParticipant p set p.lastReadMessageId = :messageId "
            + "where p.room.id = :roomId and p.member.id = :memberId and p.lastReadMessageId < :messageId")
    int advanceRead(@Param("roomId") Long roomId, @Param("memberId") Long memberId, @Param("messageId") long messageId);
}
