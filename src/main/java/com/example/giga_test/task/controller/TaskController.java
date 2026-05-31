package com.example.giga_test.task.controller;

import com.example.giga_test.common.dto.PageResponse;
import com.example.giga_test.model.Task;
import com.example.giga_test.task.dto.CreateTaskRequest;
import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.dto.UpdateClassificationRequest;
import com.example.giga_test.task.dto.TicketLifecycleDtos.*;
import com.example.giga_test.task.service.TaskService;
import com.example.giga_test.ticket.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/tickets")
public class TaskController {
    private final TaskService ticketService;

    public TaskController(TaskService ticketService) {this.ticketService = ticketService;}

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTicketById(@PathVariable("id") Long id) { return ResponseEntity.ok(ticketService.getTaskByID(id)); }

    @GetMapping
    public ResponseEntity<PageResponse<Task>> getTicketsByFilter(@RequestParam(name = "assignedTo", required = false) Long assignedTo,
                                                                 @RequestParam(name = "requester", required = false) Long requester,
                                                                 @RequestParam(name = "status", required = false) com.example.giga_test.model.Status status,
                                                                 @RequestParam(name = "priority", required = false) com.example.giga_test.model.Priority priority,
                                                                 @RequestParam(name = "category", required = false) com.example.giga_test.model.Category category,
                                                                 @RequestParam(name = "createdFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
                                                                 @RequestParam(name = "createdTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
                                                                 @RequestParam(name = "deadlineFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadlineFrom,
                                                                 @RequestParam(name = "deadlineTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadlineTo,
                                                                 @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                                 @RequestParam(name = "pageNumber", required = false) Integer pageNumber,
                                                                 @RequestParam(name = "sortBy", required = false) String sortBy,
                                                                 @RequestParam(name = "sortDir", required = false) String sortDir) {
        return ResponseEntity.ok(ticketService.searchTaskByFilter(new TaskSearchFilter(
                assignedTo, requester, status, priority, category, createdFrom, createdTo,
                deadlineFrom, deadlineTo, pageSize, pageNumber, sortBy, sortDir
        )));
    }

    @PostMapping
    public ResponseEntity<Task> createTicket(@RequestBody @Valid CreateTaskRequest request){ return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTask(request)); }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> changeStatus(@PathVariable Long id, @RequestBody @Valid ChangeStatusRequest request) {
        return ResponseEntity.ok(ticketService.changeStatus(id, request.status(), request.reason()));
    }

    @PatchMapping("/{id}/classification")
    public ResponseEntity<Task> updateClassification(@PathVariable Long id, @RequestBody @Valid UpdateClassificationRequest request) {
        return ResponseEntity.ok(ticketService.updateClassification(id, request.category(), request.priority()));
    }

    @PatchMapping("/{id}/assignee")
    public ResponseEntity<Task> assign(@PathVariable Long id, @RequestBody @Valid AssignRequest request) {
        return ResponseEntity.ok(ticketService.assign(id, request.assigneeId()));
    }

    @PatchMapping("/{id}/escalate")
    public ResponseEntity<Task> escalate(@PathVariable Long id, @RequestBody @Valid EscalateRequest request) {
        return ResponseEntity.ok(ticketService.escalate(id, request.reason()));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<Task> close(@PathVariable Long id, @RequestBody @Valid CloseRequest request) {
        return ResponseEntity.ok(ticketService.close(id, request.resolutionComment()));
    }

    @GetMapping("/{id}/comments")
    public List<TicketCommentDto> comments(@PathVariable Long id) {
        return ticketService.comments(id);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<TicketCommentDto> addComment(@PathVariable Long id, @RequestBody @Valid CreateTicketCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addComment(id, request));
    }

    @GetMapping("/{id}/status-history")
    public List<TicketStatusHistoryDto> statusHistory(@PathVariable Long id) {
        return ticketService.statusHistory(id);
    }

    @PostMapping("/{id}/ai/recommendations")
    public ResponseEntity<SavedAiRecommendationDto> saveAiRecommendation(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.saveAiRecommendation(id));
    }

    @GetMapping("/{id}/ai/recommendations")
    public List<SavedAiRecommendationDto> aiRecommendations(@PathVariable Long id) {
        return ticketService.aiRecommendations(id);
    }

    @PatchMapping("/{id}/ai/recommendations/{recommendationId}/feedback")
    public SavedAiRecommendationDto evaluateAiRecommendation(@PathVariable Long id,
                                                             @PathVariable Long recommendationId,
                                                             @RequestBody @Valid AiRecommendationFeedbackRequest request) {
        return ticketService.evaluateAiRecommendation(id, recommendationId, request);
    }
}
