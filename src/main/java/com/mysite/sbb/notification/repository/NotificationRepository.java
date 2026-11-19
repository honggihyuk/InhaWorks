package com.mysite.sbb.notification.repository;

import com.mysite.sbb.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByRecipientIdOrderByIdDesc(Long recipientId);

    List<Notification> findTop5ByRecipientIdAndReadFalseOrderByIdDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    boolean existsBySourceEventKey(String sourceEventKey);

    long countBySourceEventKeyStartingWith(String prefix);

    @Transactional
    @Modifying
    @Query("update Notification n set n.read = true where n.recipient.id = :memberId and n.read = false")
    int markAllRead(@Param("memberId") Long memberId);
}
