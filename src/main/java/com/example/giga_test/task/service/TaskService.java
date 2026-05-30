package com.example.giga_test.task.service;

import com.example.giga_test.audit.entity.AuditLog;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.User;
import com.example.giga_test.model.Task;
import com.example.giga_test.sla.service.SlaService;
import com.example.giga_test.task.dto.CreateTaskRequest;
import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository repository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SlaService slaService;
    private final AiService aiService;

    public TaskService(TaskRepository repository, UserRepository userRepository, AuditLogRepository auditLogRepository, SlaService slaService, AiService aiService) {
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

        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by(direction, sortProperty));
        User currentUser = currentUser();
        Long effectiveRequester = currentUser.getRole() == RoleName.USER ? currentUser.getId() : filter.requester();

        Specification<TaskEntity> spec = (root, query, cb) -> cb.conjunction();
        if (filter.assignedTo() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("assignedTo").get("id"), filter.assignedTo()));
        if (effectiveRequester != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("requester").get("id"), effectiveRequester));
        if (filter.status() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), filter.status()));
        if (filter.priority() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("priority"), filter.priority()));
        if (filter.category() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), filter.category()));
        if (filter.createdFrom() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), filter.createdFrom()));
        if (filter.createdTo() != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("createdAt"), filter.createdTo()));
        if (filter.deadlineFrom() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("resolutionDeadline"), filter.deadlineFrom()));
        if (filter.deadlineTo() != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("resolutionDeadline"), filter.deadlineTo()));

        Page<TaskEntity> allEntitys = repository.findAll(spec, pageable);
        return allEntitys.stream().map(this::entityToTask).toList();
    }

    public Task getTaskByID(Long id) {
        return entityToTask(getEntity(id));
    }

    @Transactional
    public Task createTask(CreateTaskRequest request) {
        User currentUser = currentUser();
        Long requesterId = currentUser.getRole() == RoleName.USER ? currentUser.getId() : request.requesterId();
        if (requesterId == null) {
            throw new IllegalArgumentException("Инициатор обращения обязателен");
        }
        var requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Инициатор не найден"));

        LocalDateTime now = LocalDateTime.now();
        if (request.resolutionDeadline() != null && !request.resolutionDeadline().isAfter(now)) {
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
        return entityToTask(savedEntity);
    }

    @Transactional
    public Task changeStatus(Long id, Status newStatus, String reason) {
        requireOperatorOrAdmin();
        TaskEntity taskEntity = getEntity(id);
        validateTransition(taskEntity.getStatus(), newStatus);
        taskEntity.setStatus(newStatus);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = repository.save(taskEntity);
        slaService.onStatusChange(saved, newStatus);
        writeAudit(saved, "STATUS_CHANGE", "Статус: " + newStatus + (reason == null ? "" : "; reason=" + reason));
        return entityToTask(saved);
    }

    @Transactional
    public Task assign(Long id, Long assigneeId) {
        requireOperatorOrAdmin();
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
        return entityToTask(saved);
    }

    @Transactional
    public Task escalate(Long id, String reason) {
        return changeStatus(id, Status.ESCALATED, reason == null ? "Эскалация" : reason);
    }

    @Transactional
    public Task close(Long id, String resolutionComment) {
        requireOperatorOrAdmin();
        TaskEntity taskEntity = getEntity(id);
        taskEntity.setResolutionComment(resolutionComment);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        repository.save(taskEntity);
        return changeStatus(id, Status.CLOSED, "Закрыт");
    }


    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new EntityNotFoundException("Текущий пользователь не найден");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Текущий пользователь не найден"));
    }

    private void requireOperatorOrAdmin() {
        RoleName role = currentUser().getRole();
        if (role != RoleName.OPERATOR && role != RoleName.ADMIN) {
            throw new AccessDeniedException("Недостаточно прав для выполнения операции");
        }
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
        TaskEntity task = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("No that id = " + id));
        User currentUser = currentUser();
        if (currentUser.getRole() == RoleName.USER && (task.getRequester() == null || !currentUser.getId().equals(task.getRequester().getId()))) {
            throw new EntityNotFoundException("No that id = " + id);
        }
        return task;
    }

    @Transactional
    public Task updateClassification(Long id, Category category, Priority priority) {
        requireOperatorOrAdmin();
        TaskEntity taskEntity = getEntity(id);
        taskEntity.setCategory(category);
        taskEntity.setPriority(priority);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = repository.save(taskEntity);
        writeAudit(saved, "CLASSIFICATION_UPDATE", "Категория=" + category + "; приоритет=" + priority);
        return entityToTask(saved);
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
        requireOperatorOrAdmin();
        TaskEntity taskEntity = getEntity(id);
        if(taskEntity.getStatus() == Status.CLOSED){
            throw new IllegalStateException("Cannot approve" + taskEntity.getStatus());
        }
        Task processedTask = sendToAI(entityToTask(taskEntity));
        TaskEntity entityToSave = taskToEntity(processedTask);
        entityToSave.setId(taskEntity.getId());
        TaskEntity savedEntity = repository.save(entityToSave);
        return entityToTask(savedEntity);
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
    private Task entityToTask(TaskEntity entity) {
        Task task = new Task();
        task.setId(entity.getId());
        task.setTaskNumber(entity.getTaskNumber());
        task.setTitle(entity.getTitle());
        task.setDescriprion(entity.getDescription());
        task.setStatus(entity.getStatus());
        task.setPriority(entity.getPriority());
        task.setCategory(entity.getCategory());
        task.setRequester(entity.getRequester());
        task.setAssignedTo(entity.getAssignedTo());
        task.setCreatedAt(entity.getCreatedAt());
        task.setResolutionDeadline(entity.getResolutionDeadline());
        task.setResolutionComment(entity.getResolutionComment());
        return task;
    }

    private TaskEntity taskToEntity(Task task) {
        TaskEntity entity = new TaskEntity();
        entity.setId(task.getId());
        entity.setTaskNumber(task.getTaskNumber());
        entity.setTitle(task.getTitle());
        entity.setDescription(task.getDescriprion());
        entity.setStatus(task.getStatus());
        entity.setPriority(task.getPriority());
        entity.setCategory(task.getCategory());
        entity.setRequester(task.getRequester());
        entity.setAssignedTo(task.getAssignedTo());
        entity.setCreatedAt(task.getCreatedAt());
        entity.setResolutionDeadline(task.getResolutionDeadline());
        entity.setResolutionComment(task.getResolutionComment());
        return entity;
    }

}
