package com.example.giga_test.task.controller;

import com.example.giga_test.model.Task;
import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.dto.TicketLifecycleDtos.*;
import com.example.giga_test.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                                                      @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                      @RequestParam(name = "pageNumber", required = false) Integer pageNumber) {
        return ResponseEntity.ok(taskService.searchTaskByFilter(new TaskSearchFilter(assignedTo, requester, pageSize, pageNumber)));
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody @Valid Task taskToCreate){ return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(taskToCreate)); }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> changeStatus(@PathVariable Long id, @RequestBody @Valid ChangeStatusRequest request) {
        return ResponseEntity.ok(taskService.changeStatus(id, request.status(), request.reason()));
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
