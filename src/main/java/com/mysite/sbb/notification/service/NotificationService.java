package com.mysite.sbb.notification.service;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.notification.entity.Notification;
import com.mysite.sbb.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 알림 : 목록, 읽음 처리, (Outbox 핸들러가 호출하는) 알림 만들기
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<Notification> getList(Member me) {
        return notificationRepository.findTop50ByRecipientIdOrderByIdDesc(me.getId());
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadTop(Member me) {
        return notificationRepository.findTop5ByRecipientIdAndReadFalseOrderByIdDesc(me.getId());
    }

    @Transactional(readOnly = true)
    public long countUnread(Member me) {
        return notificationRepository.countByRecipientIdAndReadFalse(me.getId());
    }

    // 읽음 처리하고 이동할 주소를 돌려준다 (내 알림만)
    public String read(Long id, Member me) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getRecipient().getId().equals(me.getId()))
                .orElseThrow(() -> new EntityNotFoundException("알림을 찾을 수 없어요."));
        n.setRead(true);
        return (n.getLink() == null || n.getLink().isBlank()) ? "/notifications" : n.getLink();
    }

    public void readAll(Member me) {
        notificationRepository.markAllRead(me.getId());
    }

    // [신뢰성] 멱등 처리 : 같은 키로 이미 만든 알림이 있으면 다시 만들지 않는다
    //  (먼저 exists 로 확인하고, 동시에 들어온 경우는 sourceEventKey unique 제약이 막아준다)
    public boolean createIfAbsent(String sourceEventKey, Member recipient, NotificationType type,
                                  String title, String message, String link) {
        if (notificationRepository.existsBySourceEventKey(sourceEventKey)) {
            return false;
        }
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .created(LocalDateTime.now())
                .sourceEventKey(sourceEventKey)
                .build());
        return true;
    }
}
