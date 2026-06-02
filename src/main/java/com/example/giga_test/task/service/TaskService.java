package com.example.giga_test.task.service;

import com.example.giga_test.ai.dto.AiDtos;
import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.ai.service.EmbeddingService;
import com.example.giga_test.audit.entity.AuditLog;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.common.dto.PageResponse;
import com.example.giga_test.model.*;
import com.example.giga_test.notification.service.NotificationService;
import com.example.giga_test.sla.service.SlaService;
import com.example.giga_test.task.dto.CreateTaskRequest;
import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.dto.UpdateTaskRequest;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ticket.dto.*;
import com.example.giga_test.ticket.entity.AiRecommendation;
import com.example.giga_test.ticket.entity.TicketComment;
import com.example.giga_test.ticket.entity.TicketStatusHistory;
import com.example.giga_test.ticket.repository.AiRecommendationRepository;
import com.example.giga_test.ticket.repository.TicketCommentRepository;
import com.example.giga_test.ticket.repository.TicketStatusHistoryRepository;
import com.example.giga_test.user.dto.UserSummaryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final TaskRepository repository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SlaService slaService;
    private final AiService aiService;
    private final EmbeddingService embeddingService;
    private final TicketCommentRepository commentRepository;
    private final TicketStatusHistoryRepository statusHistoryRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public TaskService(TaskRepository repository, UserRepository userRepository, AuditLogRepository auditLogRepository,
                       SlaService slaService, AiService aiService, EmbeddingService embeddingService,
                       TicketCommentRepository commentRepository, TicketStatusHistoryRepository statusHistoryRepository,
                       AiRecommendationRepository aiRecommendationRepository, NotificationService notificationService,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.slaService = slaService;
        this.aiService = aiService;
        this.embeddingService = embeddingService;
        this.commentRepository = commentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }
    @Transactional(readOnly = true)
    public PageResponse<Task> searchTaskByFilter(TaskSearchFilter filter) {
        int pageSize = normalizePageSize(filter.pageSize());
        int pageNumber = normalizePageNumber(filter.pageNumber());
        //по чему выполняется фильтрация
        String requestedSortBy = filter.sortBy() == null ? "createdAt" : filter.sortBy();
        String sortProperty = switch (requestedSortBy) {
            case "createdAt", "updatedAt", "resolutionDeadline", "priority", "status", "category" -> requestedSortBy;
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(filter.sortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;

        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by(direction, sortProperty));
        User currentUser = currentUser();
        Long effectiveRequester = currentUser.getRole() == RoleName.USER ? null : filter.requester();
        //юзеру только его заявки
        Specification<TaskEntity> spec = (root, query, cb) -> cb.conjunction();
        if (currentUser.getRole() == RoleName.USER) {
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.equal(r.get("requester").get("id"), currentUser.getId()),
                    cb.equal(r.get("createdBy"), currentUser.getUsername())
            ));
        }
        if (filter.assignedTo() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("assignedTo").get("id"), filter.assignedTo()));
        if (effectiveRequester != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("requester").get("id"), effectiveRequester));
        if (filter.status() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), filter.status()));
        if (filter.priority() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("priority"), filter.priority()));
        if (filter.category() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), filter.category()));
        if (filter.createdFrom() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), filter.createdFrom()));
        if (filter.createdTo() != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("createdAt"), filter.createdTo()));
        if (filter.deadlineFrom() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("resolutionDeadline"), filter.deadlineFrom()));
        if (filter.deadlineTo() != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("resolutionDeadline"), filter.deadlineTo()));

        Page<TaskEntity> page = repository.findAll(spec, pageable);
        return new PageResponse<>(page.stream().map(this::entityToTask).toList(), pageNumber, pageSize, page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Task getTaskByID(Long id) {
        return entityToTask(getEntity(id));
    }

    @Transactional
    public Task createTask(CreateTaskRequest request) {
        User currentUser = currentUser();
        Long requesterId = request.requesterId() == null ? currentUser.getId() : request.requesterId();
        var requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Инициатор не найден"));

        LocalDateTime now = LocalDateTime.now();
        if (request.resolutionDeadline() != null && !request.resolutionDeadline().isAfter(now)) {
            throw new IllegalArgumentException("До дедлайна минимум должен быть 1 день");
        }

        TaskEntity entityToSave = new TaskEntity();
        entityToSave.setTitle(request.title());
        entityToSave.setDescription(request.description());
        entityToSave.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        entityToSave.setCategory(request.category() == null ? Category.GENERAL : request.category());
        entityToSave.setRequester(requester);
        entityToSave.setStatus(Status.NEW);
        entityToSave.setCreatedAt(now);
        entityToSave.setResolutionDeadline(request.resolutionDeadline());
        entityToSave.setUpdatedAt(now);
        entityToSave.setCreatedBy(currentUser.getUsername());
        entityToSave.setUpdatedBy(currentUser.getUsername());

        TaskEntity savedEntity = repository.save(entityToSave);
        slaService.ensureForTicket(savedEntity);
        embeddingService.upsertTaskEmbedding(savedEntity);
        writeStatusHistory(savedEntity, null, Status.NEW, "Создано обращение", currentUser);
        writeAudit(savedEntity, "CREATE", "Создано обращение", null,
                snapshot(savedEntity), currentUser);
        notificationService.notify(requester, "Обращение создано",
                "Создано обращение #" + savedEntity.getId() + ": " + savedEntity.getTitle());
        return entityToTask(savedEntity);
    }


    @Transactional
    public Task updateTask(Long id, UpdateTaskRequest request) {
        User actor = currentUser();
        TaskEntity taskEntity = getEntity(id);
        if (actor.getRole() == RoleName.USER && taskEntity.getStatus() != Status.NEW && taskEntity.getStatus() != Status.UNASSIGNED) {
            throw new AccessDeniedException("Пользователь может редактировать только новые необработанные обращения");
        }

        String before = snapshot(taskEntity);
        Status oldStatus = taskEntity.getStatus();
        boolean shouldUpdateEmbedding = false;

        if (request.title() != null) {
            taskEntity.setTitle(request.title());
            shouldUpdateEmbedding = true;
        }
        if (request.description() != null) {
            taskEntity.setDescription(request.description());
            shouldUpdateEmbedding = true;
        }
        Priority requestedPriority = parseEnum(request.priority(), Priority.class);
        if (requestedPriority != null) {
            taskEntity.setPriority(requestedPriority);
            shouldUpdateEmbedding = true;
        }
        Category requestedCategory = parseEnum(request.category(), Category.class);
        if (requestedCategory != null) {
            taskEntity.setCategory(requestedCategory);
            shouldUpdateEmbedding = true;
        }
        LocalDateTime requestedDeadline = parseDateTime(request.resolutionDeadline());
        if (requestedDeadline != null) {
            LocalDateTime now = LocalDateTime.now();
            if (!requestedDeadline.isAfter(now)) {
                throw new IllegalArgumentException("До дедлайна минимум должен быть 1 день");
            }
            taskEntity.setResolutionDeadline(requestedDeadline);
        }
        if (request.resolutionComment() != null) {
            taskEntity.setResolutionComment(request.resolutionComment());
        }
        Long requesterId = parseLong(request.requesterId());
        if (requesterId != null && actor.getRole() != RoleName.USER) {
            var requester = userRepository.findById(requesterId)
                    .orElseThrow(() -> new EntityNotFoundException("Инициатор не найден"));
            taskEntity.setRequester(requester);
        }
        Long assigneeId = parseLong(request.assigneeId());
        if (assigneeId != null) {
            requireOperatorOrAdmin();
            var assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new EntityNotFoundException("Исполнитель не найден"));
            if (assignee.getRole() != RoleName.OPERATOR && assignee.getRole() != RoleName.ADMIN) {
                throw new IllegalArgumentException("Исполнителем может быть только оператор или администратор");
            }
            taskEntity.setAssignedTo(assignee);
        }
        Status requestedStatus = parseEnum(request.status(), Status.class);
        if (requestedStatus != null && requestedStatus != oldStatus) {
            requireOperatorOrAdmin();
            validateTransition(oldStatus, requestedStatus);
            taskEntity.setStatus(requestedStatus);
        }

        taskEntity.setUpdatedAt(LocalDateTime.now());
        taskEntity.setUpdatedBy(actor.getUsername());
        TaskEntity saved = repository.save(taskEntity);
        if (requestedStatus != null && requestedStatus != oldStatus) {
            slaService.onStatusChange(saved, saved.getStatus());
            writeStatusHistory(saved, oldStatus, saved.getStatus(), "Обновление обращения", actor);
        }
        if (shouldUpdateEmbedding) {
            embeddingService.upsertTaskEmbedding(saved);
        }
        writeAudit(saved, "UPDATE", "Обращение обновлено", before, snapshot(saved), actor);
        return entityToTask(saved);
    }

    @Transactional
    public void deleteTask(Long id) {
        User actor = currentUser();
        if (actor.getRole() != RoleName.ADMIN) {
            throw new AccessDeniedException("Удаление обращений доступно только администратору");
        }
        TaskEntity taskEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No that id = " + id));
        writeAudit(taskEntity, "DELETE", "Обращение удалено", snapshot(taskEntity), null, actor);
        repository.delete(taskEntity);
    }

    @Transactional
    public Task changeStatus(Long id, Status newStatus, String reason) {
        requireOperatorOrAdmin();
        if ((newStatus == Status.ESCALATED || newStatus == Status.CLOSED) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Комментарий обязателен для закрытия или эскалации обращения");
        }
        //получение сущности, сохранение старого статуса
        User actor = currentUser();
        TaskEntity taskEntity = getEntity(id);
        Status oldStatus = taskEntity.getStatus();
        validateTransition(oldStatus, newStatus);
        String before = snapshot(taskEntity);
        taskEntity.setStatus(newStatus);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        taskEntity.setUpdatedBy(actor.getUsername());
        //сохранение, обновление SLA, запись истории и аудита
        TaskEntity saved = repository.save(taskEntity);
        slaService.onStatusChange(saved, newStatus);
        writeStatusHistory(saved, oldStatus, newStatus, reason, actor);
        writeAudit(saved, "STATUS_CHANGE", "Статус: " + oldStatus + " -> " + newStatus + "; reason=" + reason, before, snapshot(saved), actor);
        notifyParticipants(saved, "Статус обращения изменён", "Обращение #" + saved.getId() + " переведено в статус " + newStatus);
        return entityToTask(saved);
    }

    @Transactional
    //назначение исполнителя
    public Task assign(Long id, Long assigneeId) {
        requireOperatorOrAdmin();
        User actor = currentUser();
        TaskEntity taskEntity = getEntity(id);
        String before = snapshot(taskEntity);
        var assignee = userRepository.findById(assigneeId).orElseThrow(() -> new EntityNotFoundException("Исполнитель не найден"));
        if (assignee.getRole() != RoleName.OPERATOR && assignee.getRole() != RoleName.ADMIN) {
            throw new IllegalArgumentException("Исполнителем может быть только оператор или администратор");
        }
        // получение сущности, сохранение старого статуса
        Status oldStatus = taskEntity.getStatus();
        taskEntity.setAssignedTo(assignee);
        if (taskEntity.getStatus() == Status.NEW || taskEntity.getStatus() == Status.UNASSIGNED) {
            taskEntity.setStatus(Status.ASSIGNED);
        }
        taskEntity.setUpdatedAt(LocalDateTime.now());
        taskEntity.setUpdatedBy(actor.getUsername());
        TaskEntity saved = repository.save(taskEntity);
        slaService.onStatusChange(saved, saved.getStatus());
        if (oldStatus != saved.getStatus()) writeStatusHistory(saved, oldStatus, saved.getStatus(), "Назначен исполнитель", actor);
        writeAudit(saved, "ASSIGN", "Назначен исполнитель id=" + assigneeId, before, snapshot(saved), actor);
        notificationService.notify(assignee, "Вам назначено обращение", "Обращение #" + saved.getId() + ": " + saved.getTitle());
        notifyRequester(saved, "Обращение назначено", "Обращение #" + saved.getId() + " назначено исполнителю");
        return entityToTask(saved);
    }

    @Transactional
    public Task escalate(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Комментарий обязателен для эскалации обращения");
        return changeStatus(id, Status.ESCALATED, reason);
    }

    @Transactional
    public Task close(Long id, String resolutionComment) {
        requireOperatorOrAdmin();
        if (resolutionComment == null || resolutionComment.isBlank()) throw new IllegalArgumentException("Комментарий обязателен для закрытия обращения");
        User actor = currentUser();
        TaskEntity taskEntity = getEntity(id);
        String before = snapshot(taskEntity);
        taskEntity.setResolutionComment(resolutionComment);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        taskEntity.setUpdatedBy(actor.getUsername());
        repository.save(taskEntity);
        writeAudit(taskEntity, "RESOLUTION_COMMENT", "Комментарий решения обновлён", before, snapshot(taskEntity), actor);
        return changeStatus(id, Status.CLOSED, resolutionComment);
    }

    @Transactional
    public Task updateClassification(Long id, Category category, Priority priority) {
        requireOperatorOrAdmin();
        User actor = currentUser();
        TaskEntity taskEntity = getEntity(id);
        String before = snapshot(taskEntity);
        boolean changed = !Objects.equals(taskEntity.getCategory(), category) || !Objects.equals(taskEntity.getPriority(), priority);
        taskEntity.setCategory(category);
        taskEntity.setPriority(priority);
        taskEntity.setUpdatedAt(LocalDateTime.now());
        taskEntity.setUpdatedBy(actor.getUsername());
        TaskEntity saved = repository.save(taskEntity);
        embeddingService.upsertTaskEmbedding(saved);
        writeAudit(saved, changed ? "CLASSIFICATION_UPDATE" : "CLASSIFICATION_CONFIRM", "Категория=" + category + "; приоритет=" + priority, before, snapshot(saved), actor);
        return entityToTask(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketCommentDto> comments(Long id) {
        TaskEntity task = getEntity(id);
        User current = currentUser();
        boolean canSeeInternal = current.getRole() == RoleName.OPERATOR || current.getRole() == RoleName.ADMIN;
        return commentRepository.findAllByTicketIdOrderByCreatedAtAsc(task.getId()).stream()
                .filter(c -> canSeeInternal || !c.isInternalComment())
                .map(this::toCommentDto)
                .toList();
    }
    @Transactional
    public TicketCommentDto addComment(Long id, CreateTicketCommentRequest request) {
        TaskEntity ticket = getEntity(id);
        User actor = currentUser();
        boolean internal = request.internalComment();
        if (internal && actor.getRole() != RoleName.OPERATOR && actor.getRole() != RoleName.ADMIN) {
            throw new AccessDeniedException("Внутренние комментарии доступны только оператору и администратору");
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(actor);
        comment.setCommentText(request.commentText());
        comment.setInternalComment(internal);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setCreatedBy(actor.getUsername());
        comment.setUpdatedBy(actor.getUsername());
        TicketComment saved = commentRepository.save(comment);
        writeAudit(ticket, "COMMENT_ADD", (internal ? "Внутренний" : "Публичный") + " комментарий добавлен",
                null, request.commentText(), actor);
        //уведомление пользователя
        if (!internal) notifyParticipants(ticket, "Новый комментарий", "В обращении #" + ticket.getId() + " добавлен комментарий");
        return toCommentDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketStatusHistoryDto> statusHistory(Long id) {
        TaskEntity ticket = getEntity(id);
        return statusHistoryRepository.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream().map(this::toHistoryDto).toList();
    }

    @Transactional
    //cохранение AI-рекомендации
    public SavedAiRecommendationDto saveAiRecommendation(Long id) {
        requireOperatorOrAdmin();
        User actor = currentUser();
        TaskEntity ticket = getEntity(id);
        AiDtos.RecommendResponse response = aiService.recommend((ticket.getTitle() + "\n" + ticket.getDescription()).trim());
        AiRecommendation entity = new AiRecommendation();
        // заполнение полей из response
        entity.setTicket(ticket);
        entity.setRecommendation(response.recommendation());
        entity.setStepsJson(toJson(response.steps()));
        if (response.explainability() != null) {
            entity.setMode(response.explainability().mode());
            entity.setSourcesJson(toJson(response.explainability().sources()));
            entity.setLlmStatus(response.explainability().llmStatus());
            entity.setRawModelOutput(response.explainability().rawModelOutput());
        }
        entity.setCreatedByUser(actor);
        entity.setCreatedAt(LocalDateTime.now());
        AiRecommendation saved = aiRecommendationRepository.save(entity);
        writeAudit(ticket, "AI_RECOMMENDATION_SAVE", "AI-рекомендация сохранена",
                null, response.recommendation(), actor);
        return toAiRecommendationDto(saved);
    }

    @Transactional
    public SavedAiRecommendationDto saveAiRecommendationDraft(Long id, SaveAiRecommendationRequest request) {
        requireOperatorOrAdmin();
        User actor = currentUser();
        TaskEntity ticket = getEntity(id);
        AiRecommendation entity = new AiRecommendation();
        entity.setTicket(ticket);
        entity.setRecommendation(request.recommendation());
        entity.setStepsJson(toJson(request.steps() == null ? List.of() : request.steps()));
        entity.setMode(request.mode());
        entity.setSourcesJson(toJson(request.sources() == null ? List.of() : request.sources()));
        entity.setLlmStatus(request.llmStatus());
        entity.setRawModelOutput(request.rawModelOutput());
        entity.setCreatedByUser(actor);
        entity.setCreatedAt(LocalDateTime.now());
        AiRecommendation saved = aiRecommendationRepository.save(entity);
        writeAudit(ticket, "AI_RECOMMENDATION_SAVE_DRAFT", "AI-рекомендация сохранена из черновика",
                null, request.recommendation(), actor);
        return toAiRecommendationDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedAiRecommendationDto> aiRecommendations(Long id) {
        TaskEntity ticket = getEntity(id);
        return aiRecommendationRepository.findAllByTicketIdOrderByCreatedAtDesc(ticket.getId()).stream().map(this::toAiRecommendationDto).toList();
    }

    @Transactional
    public SavedAiRecommendationDto evaluateAiRecommendation(Long ticketId, Long recommendationId, AiRecommendationFeedbackRequest request) {
        requireOperatorOrAdmin();
        User actor = currentUser();
        TaskEntity ticket = getEntity(ticketId);
        AiRecommendation recommendation = aiRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new EntityNotFoundException("AI-рекомендация не найдена: " + recommendationId));
        if (!recommendation.getTicket().getId().equals(ticket.getId())) {
            throw new EntityNotFoundException("AI-рекомендация не найдена: " + recommendationId);
        }
        recommendation.setAccepted(request.accepted());
        recommendation.setUsefulnessScore(request.usefulnessScore());
        recommendation.setFeedbackComment(request.feedbackComment());
        recommendation.setEvaluatedByUser(actor);
        recommendation.setEvaluatedAt(LocalDateTime.now());
        AiRecommendation saved = aiRecommendationRepository.save(recommendation);
        writeAudit(ticket, "AI_RECOMMENDATION_EVALUATE", "accepted=" + request.accepted() + "; score=" + request.usefulnessScore(), null, request.feedbackComment(), actor);
        return toAiRecommendationDto(saved);
    }

    public Task aiProcessingTask(Long id) {
        requireOperatorOrAdmin();
        TaskEntity taskEntity = getEntity(id);
        if(taskEntity.getStatus() == Status.CLOSED){
            throw new IllegalStateException("Cannot approve" + taskEntity.getStatus());
        }
        Task processedTask = sendToAI(entityToTask(taskEntity));
        return updateClassification(id, processedTask.getCategory(), processedTask.getPriority());
    }

    private Task sendToAI(Task task) {
        var result = aiService.classify((task.getTitle() + " " + task.getDescription()).trim());
        Category category = Category.valueOf(result.category());
        Priority priority = Priority.valueOf(result.priority());
        return task.toBuilder().category(category).priority(priority).build();
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
    //проверка перед сменой статуса
    private void validateTransition(Status current, Status target) {
        if (current == Status.CLOSED || current == Status.CANCELED) {
            throw new IllegalStateException("Нельзя менять финальный статус " + current);
        }
        if (current == Status.NEW && target == Status.RESOLVED) {
            throw new IllegalStateException("Нельзя перейти из NEW сразу в RESOLVED");
        }
    }

    private TaskEntity getEntity(Long id) {
        User currentUser = currentUser();
        var taskOptional = repository.findById(id);
        if (taskOptional.isEmpty()) {
            if (currentUser.getRole() == RoleName.USER) {
                throw new AccessDeniedException("Нет доступа к чужому обращению");
            }
            throw new EntityNotFoundException("No that id = " + id);
        }
        TaskEntity task = taskOptional.get();
        if (currentUser.getRole() == RoleName.USER) {
            boolean requesterMatches = task.getRequester() != null && currentUser.getId().equals(task.getRequester().getId());
            boolean creatorMatches = currentUser.getUsername().equals(task.getCreatedBy());
            if (!requesterMatches && !creatorMatches) {
                throw new AccessDeniedException("Нет доступа к чужому обращению");
            }
        }
        return task;
    }

    private int normalizePageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private int normalizePageNumber(Integer requested) {
        if (requested == null || requested < 0) return 0;
        return requested;
    }

    //история изменения статуса
    private void writeStatusHistory(TaskEntity ticket, Status from, Status to, String reason, User actor) {
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicket(ticket);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setChangedBy(actor);
        history.setCreatedAt(LocalDateTime.now());
        statusHistoryRepository.save(history);
    }

    private void writeAudit(TaskEntity ticket, String action, String details, String before, String after, User actor) {
        AuditLog audit = new AuditLog();
        audit.setActor(actor);
        audit.setAction(action);
        audit.setEntityType("TICKET");
        audit.setEntityId(String.valueOf(ticket.getId()));
        audit.setDetails(details);
        audit.setBeforeValue(before);
        audit.setAfterValue(after);
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            audit.setIpAddress(clientIp(request));
            audit.setUserAgent(request.getHeader("User-Agent"));
        }
        audit.setCreatedAt(LocalDateTime.now());
        audit.setUpdatedAt(LocalDateTime.now());
        audit.setCreatedBy(actor == null ? null : actor.getUsername());
        audit.setUpdatedBy(actor == null ? null : actor.getUsername());
        auditLogRepository.save(audit);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) return forwardedFor.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private void notifyParticipants(TaskEntity ticket, String subject, String message) {
        notifyRequester(ticket, subject, message);
        if (ticket.getAssignedTo() != null && (ticket.getRequester() == null || !ticket.getAssignedTo().getId().equals(ticket.getRequester().getId()))) {
            notificationService.notify(ticket.getAssignedTo(), subject, message);
        }
    }

    private void notifyRequester(TaskEntity ticket, String subject, String message) {
        notificationService.notify(ticket.getRequester(), subject, message);
    }

    private String snapshot(TaskEntity e) {
        return "{id=" + e.getId() + ", status=" + e.getStatus() + ", category=" + e.getCategory() + ", priority=" + e.getPriority() + ", assignedTo=" + (e.getAssignedTo() == null ? null : e.getAssignedTo().getId()) + ", resolutionComment=" + e.getResolutionComment() + "}";
    }

    private <E extends Enum<E>> E parseEnum(Object rawValue, Class<E> enumClass) {
        String normalized = enumToken(rawValue);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, normalized.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Long parseLong(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        String token = enumToken(rawValue);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(token.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof LocalDateTime value) {
            return value;
        }
        String token = enumToken(rawValue);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(token.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String enumToken(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String value) {
            return value;
        }
        if (rawValue instanceof Map<?, ?> map) {
            for (String key : List.of("code", "name", "value", "id")) {
                Object value = map.get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        return String.valueOf(rawValue);
    }

    private Task entityToTask(TaskEntity entity) {
        Task task = new Task();
        task.setId(entity.getId());
        task.setTaskNumber(entity.getTaskNumber());
        task.setTitle(entity.getTitle());
        task.setDescription(entity.getDescription());
        task.setStatus(entity.getStatus());
        task.setPriority(entity.getPriority());
        task.setCategory(entity.getCategory());
        task.setRequester(toResponseUser(entity.getRequester()));
        task.setAssignedTo(toResponseUser(entity.getAssignedTo()));
        task.setCreatedAt(entity.getCreatedAt());
        task.setResolutionDeadline(entity.getResolutionDeadline());
        task.setResolutionComment(entity.getResolutionComment());
        return task;
    }


    private User toResponseUser(User user) {
        if (user == null) {
            return null;
        }
        return User.builder()
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .updatedBy(user.getUpdatedBy())
                .build();
    }

    private TicketCommentDto toCommentDto(TicketComment comment) {
        return new TicketCommentDto(comment.getId(), comment.getTicket().getId(), UserSummaryDto.from(comment.getAuthor()), comment.getCommentText(), comment.isInternalComment(), comment.getCreatedAt(), comment.getUpdatedAt());
    }

    private TicketStatusHistoryDto toHistoryDto(TicketStatusHistory h) {
        return new TicketStatusHistoryDto(h.getId(), h.getTicket().getId(), h.getFromStatus(), h.getToStatus(), h.getReason(), h.getChangedBy() == null ? null : UserSummaryDto.from(h.getChangedBy()), h.getCreatedAt());
    }

    private SavedAiRecommendationDto toAiRecommendationDto(AiRecommendation r) {
        return new SavedAiRecommendationDto(r.getId(), r.getTicket().getId(), r.getRecommendation(), fromJsonList(r.getStepsJson()), r.getMode(), fromJsonList(r.getSourcesJson()), r.getLlmStatus(), r.getRawModelOutput(), fallbackReason(r.getRawModelOutput()), r.getAccepted(), r.getUsefulnessScore(), r.getFeedbackComment(), r.getCreatedByUser() == null ? null : UserSummaryDto.from(r.getCreatedByUser()), r.getEvaluatedByUser() == null ? null : UserSummaryDto.from(r.getEvaluatedByUser()), r.getCreatedAt(), r.getEvaluatedAt());
    }

    private String fallbackReason(String rawModelOutput) {
        String normalized = rawModelOutput == null ? "" : rawModelOutput.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("429") || normalized.contains("квота") || normalized.contains("too many")) {
            return "Сработал fallback: внешний LLM-сервис вернул ограничение частоты запросов или исчерпание квоты.";
        }
        if (normalized.contains("сети") || normalized.contains("dns") || normalized.contains("network")) {
            return "Сработал fallback: внешний LLM-сервис недоступен из-за сетевой ошибки или ошибки DNS.";
        }
        if (normalized.contains("misconfigured") || normalized.contains("auth key")) {
            return "Сработал fallback: не настроен ключ доступа к внешнему LLM-сервису.";
        }
        if (normalized.contains("temporary unavailable") || normalized.contains("manual triage") || normalized.contains("недоступ")) {
            return "Сработал fallback: внешний LLM-сервис временно недоступен.";
        }
        return null;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize list", e);
            return "[]";
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Cannot deserialize list", e);
            return List.of();
        }
    }
}
