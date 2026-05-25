package com.example.giga_test.task.service;

import com.example.giga_test.audit.entity.AuditLog;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.Task;
import com.example.giga_test.sla.service.SlaService;
import com.example.giga_test.task.dto.CreateTaskRequest;
import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.mapper.TaskMapper;
import com.example.giga_test.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskMapper mapper;
    private final TaskRepository repository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SlaService slaService;
    private final AiService aiService;

    public TaskService(TaskMapper mapper, TaskRepository repository, UserRepository userRepository, AuditLogRepository auditLogRepository, SlaService slaService, AiService aiService) {
        this.mapper = mapper;
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.slaService = slaService;
        this.aiService = aiService;
    }

    public List<Task> searchTaskByFilter(TaskSearchFilter filter) {
        int pageSize = filter.pageSize() != null ? filter.pageSize() : 10;
        int pageNumber = filter.pageNumber() != null ? filter.pageNumber() : 0;

        String requestedSortBy = filter.sortBy() == null ? "createdAt" : filter.sortBy();
        String sortProperty = switch (requestedSortBy) {
            case "createdAt", "updatedAt", "resolutionDeadline", "priority", "status" -> requestedSortBy;
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(filter.sortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;

        var pageable = Pageable.ofSize(pageSize).withPage(pageNumber).withSort(Sort.by(direction, sortProperty));
        Page<TaskEntity> allEntitys = repository.searchByFilter(
                filter.assignedTo(),
                filter.requester(),
                filter.status(),
                filter.priority(),
                filter.category(),
                filter.createdFrom(),
                filter.createdTo(),
                filter.deadlineFrom(),
                filter.deadlineTo(),
                pageable
        );
        return allEntitys.stream().map(mapper::entityToTask).toList();
    }

    public Task getTaskByID(Long id) {
        return mapper.entityToTask(getEntity(id));
    }

    public Task createTask(CreateTaskRequest request) {
        var requester = userRepository.findById(request.requesterId())
                .orElseThrow(() -> new EntityNotFoundException("Инициатор не найден"));

        LocalDateTime now = LocalDateTime.now();
        if (!request.resolutionDeadline().isAfter(now)) {
            throw new IllegalArgumentException("До дедлайна минимум должен быть 1 день");
        }

        TaskEntity entityToSave = new TaskEntity();
        entityToSave.setId(null);
        entityToSave.setTitle(request.title());
        entityToSave.setDescription(request.description());
        entityToSave.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        entityToSave.setCategory(request.category() == null ? Category.GENERAL : request.category());
        entityToSave.setRequester(requester);
        entityToSave.setStatus(Status.NEW);
        entityToSave.setCreatedAt(now);
        entityToSave.setResolutionDeadline(request.resolutionDeadline());
        entityToSave.setUpdatedAt(now);

        TaskEntity savedEntity = repository.save(entityToSave);
        slaService.ensureForTicket(savedEntity);
        writeAudit(savedEntity, "CREATE", "Создан тикет");
        return mapper.entityToTask(savedEntity);
    }

    @Transactional
    public Task changeStatus(Long id, Status newStatus, String reason) {
        TaskEntity taskEntity = getEntity(id);
        validateTransition(taskEntity.getStatus(), newStatus);
        taskEntity.setStatus(newStatus);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = repository.save(taskEntity);
        slaService.onStatusChange(saved, newStatus);
        writeAudit(saved, "STATUS_CHANGE", "Статус: " + newStatus + (reason == null ? "" : "; reason=" + reason));
        return mapper.entityToTask(saved);
    }

    @Transactional
    public Task assign(Long id, Long assigneeId) {
        TaskEntity taskEntity = getEntity(id);
        var assignee = userRepository.findById(assigneeId).orElseThrow(() -> new EntityNotFoundException("Исполнитель не найден"));
        taskEntity.setAssignedTo(assignee);
        if (taskEntity.getStatus() == Status.NEW || taskEntity.getStatus() == Status.UNASSIGNED) {
            taskEntity.setStatus(Status.ASSIGNED);
        }
        taskEntity.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = repository.save(taskEntity);
        slaService.onStatusChange(saved, saved.getStatus());
        writeAudit(saved, "ASSIGN", "Назначен исполнитель id=" + assigneeId);
        return mapper.entityToTask(saved);
    }

    @Transactional
    public Task escalate(Long id, String reason) {
        return changeStatus(id, Status.ESCALATED, reason == null ? "Эскалация" : reason);
    }

    @Transactional
    public Task close(Long id, String resolutionComment) {
        TaskEntity taskEntity = getEntity(id);
        taskEntity.setResolutionComment(resolutionComment);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        repository.save(taskEntity);
        return changeStatus(id, Status.CLOSED, "Закрыт");
    }

    private void validateTransition(Status current, Status target) {
        if (current == Status.CLOSED || current == Status.CANCELED) {
            throw new IllegalStateException("Нельзя менять финальный статус " + current);
        }
        if (current == Status.NEW && target == Status.RESOLVED) {
            throw new IllegalStateException("Нельзя перейти из NEW сразу в RESOLVED");
        }
    }

    private TaskEntity getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("No that id = " + id));
    }

    private void writeAudit(TaskEntity ticket, String action, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType("TICKET");
        log.setEntityId(String.valueOf(ticket.getId()));
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    public Task aiProcessingTask(Long id) {
        TaskEntity taskEntity = getEntity(id);
        if(taskEntity.getStatus() == Status.CLOSED){
            throw new IllegalStateException("Cannot approve" + taskEntity.getStatus());
        }
        Task processedTask = sendToAI(mapper.entityToTask(taskEntity));
        TaskEntity entityToSave = mapper.taskToEntity(processedTask);
        entityToSave.setId(taskEntity.getId());
        TaskEntity savedEntity = repository.save(entityToSave);
        return mapper.entityToTask(savedEntity);
    }

    private Task sendToAI(Task task) {
        var result = aiService.classify((task.getTitle() + " " + task.getDescriprion()).trim());
        Category category;
        try {
            category = Category.valueOf(result.category());
        } catch (Exception ex) {
            category = Category.GENERAL;
        }
        return task.toBuilder().category(category).build();
    }
}
