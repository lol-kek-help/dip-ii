package com.example.giga_test.notification.controller;

import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.notification.entity.Notification;
import com.example.giga_test.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Notification> list(Authentication authentication) {
        return notificationService.list(currentUser(authentication));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("unread", notificationService.unreadCount(currentUser(authentication)));
    }

    @PatchMapping("/{id}/read")
    public Notification markRead(Authentication authentication, @PathVariable Long id) {
        return notificationService.markRead(currentUser(authentication), id);
    }

    @PatchMapping("/read-all")
    public void markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUser(authentication));
    }

    private com.example.giga_test.model.User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new EntityNotFoundException("Текущий пользователь не найден");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Текущий пользователь не найден"));
    }
}
