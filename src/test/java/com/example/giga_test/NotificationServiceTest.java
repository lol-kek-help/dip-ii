package com.example.giga_test;

import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.User;
import com.example.giga_test.notification.entity.Notification;
import com.example.giga_test.notification.repository.NotificationRepository;
import com.example.giga_test.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    void notifyShouldCreateInAppUnreadNotification() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        User user = user(1L, "user1");

        service.notify(user, "Тема", "Сообщение");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification notification = captor.getValue();
        assertEquals(user, notification.getUser());
        assertEquals("IN_APP", notification.getChannel());
        assertEquals("Тема", notification.getSubject());
        assertFalse(notification.isRead());
        assertNotNull(notification.getCreatedAt());
        assertNotNull(notification.getUpdatedAt());
    }

    @Test
    void notifyShouldIgnoreNullUser() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repository);

        service.notify(null, "Тема", "Сообщение");

        verify(repository, never()).save(any());
    }

    @Test
    void listUnreadAndMarkAllReadShouldDelegateToRepository() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        User user = user(1L, "user1");
        Notification notification = notification(10L, user, false);

        when(repository.findAllByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(notification));
        when(repository.countByUserAndReadFalse(user)).thenReturn(1L);

        assertEquals(List.of(notification), service.list(user));
        assertEquals(1L, service.unreadCount(user));
        service.markAllRead(user);

        assertTrue(notification.isRead());
        verify(repository).saveAll(List.of(notification));
    }

    @Test
    void markReadShouldRejectForeignNotification() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        User owner = user(1L, "owner");
        User other = user(2L, "other");
        Notification notification = notification(10L, other, false);

        when(repository.findById(10L)).thenReturn(Optional.of(notification));

        assertThrows(EntityNotFoundException.class, () -> service.markRead(owner, 10L));
    }

    @Test
    void markReadShouldSetReadFlagForOwner() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        User user = user(1L, "user1");
        Notification notification = notification(10L, user, false);

        when(repository.findById(10L)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);

        Notification result = service.markRead(user, 10L);

        assertTrue(result.isRead());
        assertNotNull(result.getUpdatedAt());
        verify(repository).save(notification);
    }

    private Notification notification(Long id, User user, boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUser(user);
        notification.setSubject("subject");
        notification.setMessage("message");
        notification.setChannel("IN_APP");
        notification.setRead(read);
        return notification;
    }

    private User user(Long id, String username) {
        return User.builder().id(id).username(username).name(username).role(RoleName.USER).build();
    }
}
