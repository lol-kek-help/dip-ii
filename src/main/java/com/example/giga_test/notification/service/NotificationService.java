package com.example.giga_test.notification.service;

import com.example.giga_test.model.User;
import com.example.giga_test.notification.entity.Notification;
import com.example.giga_test.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(User user, String subject, String message) {
        if (user == null) return;
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setChannel("IN_APP");
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> list(User user) {
        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public Notification markRead(User user, Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Уведомление не найдено: " + id));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new EntityNotFoundException("Уведомление не найдено: " + id);
        }
        notification.setRead(true);
        notification.setUpdatedAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(User user) {
        List<Notification> notifications = notificationRepository.findAllByUserOrderByCreatedAtDesc(user);
        LocalDateTime now = LocalDateTime.now();
        notifications.forEach(n -> {
            n.setRead(true);
            n.setUpdatedAt(now);
        });
        notificationRepository.saveAll(notifications);
    }
}
