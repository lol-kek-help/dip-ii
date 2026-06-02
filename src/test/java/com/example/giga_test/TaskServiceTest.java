package com.example.giga_test;

import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.ai.service.EmbeddingService;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.User;
import com.example.giga_test.notification.service.NotificationService;
import com.example.giga_test.sla.service.SlaService;
import com.example.giga_test.task.dto.CreateTaskRequest;
import com.example.giga_test.task.dto.UpdateTaskRequest;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.task.service.TaskService;
import com.example.giga_test.ticket.repository.AiRecommendationRepository;
import com.example.giga_test.ticket.repository.TicketCommentRepository;
import com.example.giga_test.ticket.repository.TicketStatusHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTaskShouldSetDefaultsAndCallSideEffects() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        SlaService slaService = mock(SlaService.class);
        AiService aiService = mock(AiService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        TicketCommentRepository commentRepository = mock(TicketCommentRepository.class);
        TicketStatusHistoryRepository statusHistoryRepository = mock(TicketStatusHistoryRepository.class);
        AiRecommendationRepository aiRecommendationRepository = mock(AiRecommendationRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        TaskService service = new TaskService(taskRepository, userRepository, auditLogRepository, slaService, aiService,
                embeddingService, commentRepository, statusHistoryRepository, aiRecommendationRepository,
                notificationService, new ObjectMapper());
        User currentUser = user(10L, "user1", RoleName.USER);
        authenticateAs(currentUser.getUsername());

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(10L)).thenReturn(Optional.of(currentUser));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        var result = service.createTask(new CreateTaskRequest(
                "Не работает почта",
                "Outlook не принимает входящие письма",
                null,
                null,
                null,
                LocalDateTime.now().plusDays(1)
        ));

        assertEquals(100L, result.getId());
        assertEquals(Status.NEW, result.getStatus());
        assertEquals(Priority.MEDIUM, result.getPriority());
        assertEquals(Category.GENERAL, result.getCategory());
        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(taskCaptor.capture());
        TaskEntity savedTask = taskCaptor.getValue();
        assertEquals(currentUser, savedTask.getRequester());
        verify(slaService).ensureForTicket(savedTask);
        verify(embeddingService).upsertTaskEmbedding(savedTask);
        verify(statusHistoryRepository).save(any());
        verify(auditLogRepository).save(any());
        verify(notificationService).notify(currentUser, "Обращение создано", "Создано обращение #100: Не работает почта");
    }

    @Test
    void createTaskShouldRejectPastDeadline() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskService service = new TaskService(taskRepository, userRepository, mock(AuditLogRepository.class),
                mock(SlaService.class), mock(AiService.class), mock(EmbeddingService.class),
                mock(TicketCommentRepository.class), mock(TicketStatusHistoryRepository.class),
                mock(AiRecommendationRepository.class), mock(NotificationService.class), new ObjectMapper());
        User currentUser = user(10L, "user1", RoleName.USER);
        authenticateAs(currentUser.getUsername());

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(10L)).thenReturn(Optional.of(currentUser));

        assertThrows(IllegalArgumentException.class, () -> service.createTask(new CreateTaskRequest(
                "Просроченный дедлайн",
                "Проверка отрицательного сценария",
                Priority.LOW,
                Category.GENERAL,
                null,
                LocalDateTime.now().minusMinutes(1)
        )));
    }

    @Test
    void updateTaskShouldUpdatePriorityAndAuditChange() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        TaskService service = new TaskService(taskRepository, userRepository, auditLogRepository, mock(SlaService.class),
                mock(AiService.class), embeddingService, mock(TicketCommentRepository.class),
                mock(TicketStatusHistoryRepository.class), mock(AiRecommendationRepository.class),
                mock(NotificationService.class), new ObjectMapper());
        User operator = user(20L, "operator1", RoleName.OPERATOR);
        User requester = user(10L, "user1", RoleName.USER);
        TaskEntity existing = task(100L, requester, Status.NEW, Priority.MEDIUM, Category.GENERAL);
        authenticateAs(operator.getUsername());

        when(userRepository.findByUsername("operator1")).thenReturn(Optional.of(operator));
        when(taskRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateTask(100L, new UpdateTaskRequest(
                "Обновленная тема", null, Priority.URGENT, Category.INCIDENT, null,
                null, null, null, null
        ));

        assertEquals(Priority.URGENT, result.getPriority());
        assertEquals(Category.INCIDENT, result.getCategory());
        assertEquals("Обновленная тема", result.getTitle());
        verify(embeddingService).upsertTaskEmbedding(existing);
        verify(auditLogRepository).save(any());
    }

    @Test
    void deleteTaskShouldBeAvailableForAdminOnly() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        TaskService service = new TaskService(taskRepository, userRepository, auditLogRepository, mock(SlaService.class),
                mock(AiService.class), mock(EmbeddingService.class), mock(TicketCommentRepository.class),
                mock(TicketStatusHistoryRepository.class), mock(AiRecommendationRepository.class),
                mock(NotificationService.class), new ObjectMapper());
        User admin = user(1L, "admin1", RoleName.ADMIN);
        User requester = user(10L, "user1", RoleName.USER);
        TaskEntity existing = task(100L, requester, Status.NEW, Priority.MEDIUM, Category.GENERAL);
        authenticateAs(admin.getUsername());

        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(taskRepository.findById(100L)).thenReturn(Optional.of(existing));

        service.deleteTask(100L);

        verify(auditLogRepository).save(any());
        verify(taskRepository).delete(existing);
    }

    @Test
    void userShouldReceiveForbiddenForForeignTicket() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskService service = new TaskService(taskRepository, userRepository, mock(AuditLogRepository.class),
                mock(SlaService.class), mock(AiService.class), mock(EmbeddingService.class),
                mock(TicketCommentRepository.class), mock(TicketStatusHistoryRepository.class),
                mock(AiRecommendationRepository.class), mock(NotificationService.class), new ObjectMapper());
        User currentUser = user(10L, "user1", RoleName.USER);
        User otherUser = user(11L, "user2", RoleName.USER);
        TaskEntity foreignTask = task(100L, otherUser, Status.NEW, Priority.MEDIUM, Category.GENERAL);
        foreignTask.setCreatedBy("user2");
        authenticateAs(currentUser.getUsername());

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(currentUser));
        when(taskRepository.findById(100L)).thenReturn(Optional.of(foreignTask));

        assertThrows(AccessDeniedException.class, () -> service.getTaskByID(100L));
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null));
    }

    private TaskEntity task(Long id, User requester, Status status, Priority priority, Category category) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setTitle("Тема");
        task.setDescription("Описание");
        task.setRequester(requester);
        task.setStatus(status);
        task.setPriority(priority);
        task.setCategory(category);
        task.setCreatedBy(requester.getUsername());
        task.setUpdatedBy(requester.getUsername());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private User user(Long id, String username, RoleName role) {
        return User.builder()
                .id(id)
                .username(username)
                .name(username)
                .passwordHash("hash")
                .role(role)
                .build();
    }
}
