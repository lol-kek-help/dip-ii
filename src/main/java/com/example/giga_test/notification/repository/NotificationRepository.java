package com.example.giga_test.notification.repository;

import com.example.giga_test.model.User;
import com.example.giga_test.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
    long countByUserAndReadFalse(User user);
}
