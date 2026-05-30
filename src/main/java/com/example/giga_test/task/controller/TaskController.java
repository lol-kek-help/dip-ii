package com.example.giga_test.task.controller;

import com.example.giga_test.model.Task;
import com.example.giga_test.task.dto.CreateTaskRequest;
import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.dto.UpdateClassificationRequest;
import com.example.giga_test.task.dto.TicketLifecycleDtos.*;
import com.example.giga_test.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping({"/tickets", "/task"})
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {this.taskService = taskService;}

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskByID(@PathVariable("id") Long id) { return ResponseEntity.ok(taskService.getTaskByID(id)); }

    @GetMapping
    public ResponseEntity<List<Task>> getTaskByFilter(@RequestParam(name = "assignedTo", required = false) Long assignedTo,
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
        return ResponseEntity.ok(taskService.searchTaskByFilter(new TaskSearchFilter(
                assignedTo,
                requester,
                status,
                priority,
                category,
                createdFrom,
                createdTo,
                deadlineFrom,
                deadlineTo,
                pageSize,
                pageNumber,
                sortBy,
                sortDir
        )));
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody @Valid CreateTaskRequest request){ return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request)); }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> changeStatus(@PathVariable Long id, @RequestBody @Valid ChangeStatusRequest request) {
        return ResponseEntity.ok(taskService.changeStatus(id, request.status(), request.reason()));
    }


    @PatchMapping("/{id}/classification")
    public ResponseEntity<Task> updateClassification(@PathVariable Long id, @RequestBody @Valid UpdateClassificationRequest request) {
        return ResponseEntity.ok(taskService.updateClassification(id, request.category(), request.priority()));
    }

    @PatchMapping("/{id}/assignee")
    public ResponseEntity<Task> assign(@PathVariable Long id, @RequestBody @Valid AssignRequest request) {
        return ResponseEntity.ok(taskService.assign(id, request.assigneeId()));
    }

    @PatchMapping("/{id}/escalate")
    public ResponseEntity<Task> escalate(@PathVariable Long id, @RequestBody(required = false) EscalateRequest request) {
        return ResponseEntity.ok(taskService.escalate(id, request == null ? null : request.reason()));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<Task> close(@PathVariable Long id, @RequestBody(required = false) CloseRequest request) {
        return ResponseEntity.ok(taskService.close(id, request == null ? null : request.resolutionComment()));
    }
}
