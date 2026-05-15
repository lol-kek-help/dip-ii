package com.example.giga_test.controller;

import com.example.giga_test.model.Task;
import com.example.giga_test.service.TaskSearchFilter;
import com.example.giga_test.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/task")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping({"/{id}"})
    public ResponseEntity<Task> getTaskByID(
            @PathVariable("id") Long id
    ) {
        log.info("called getTaskByID " + id);
        return ResponseEntity.ok(taskService.getTaskByID(id));
    }
    @GetMapping("/all")
    public ResponseEntity<List<Task>> getAllTask() {
        log.info("called getAllTask");
        return ResponseEntity.ok(taskService.findAllTask());
    }
    @GetMapping("/")
    public ResponseEntity<List<Task>> getTaskByFilter(
            @RequestParam (name = "assignedTo", required = false) Long assignedTo,
            @RequestParam (name = "requester", required = false) Long requester,
            @RequestParam (name = "pageSize", required = false) Integer pageSize,
            @RequestParam (name = "pageNumber", required = false) Integer pageNumber
    ) {
        log.info("called getTaskByFilter");
        var filter = new TaskSearchFilter(
                assignedTo,
                requester,
                pageSize,
                pageNumber
        );
        return ResponseEntity.ok(taskService.searchTaskByFilter(filter));
    }
    @PostMapping("/new-task")
    public ResponseEntity<Task> createTask (
            @RequestBody @Valid Task taskToCreate
    ){
        log.info("new Task");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(taskToCreate));
    }
    @PutMapping("/{id}/update")
    public ResponseEntity<Task> updateTask(
            @PathVariable("id") Long id,
            @RequestBody @Valid Task taskToUpdate
    ) {
        log.info("called updateTask " + id);
        var updated = taskService.updateTask(id, taskToUpdate);
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelTask(
            @PathVariable("id") Long id
    ) {
        log.info("cancel Task " + id);
        taskService.cancelTask(id);
        return ResponseEntity.ok().build();


    }
    @PostMapping("/{id}/ai-processing")
    public ResponseEntity<Task> aiProcessingTask(
            @PathVariable("id") Long id
    ){
        log.info("approve Task " + id);
        var task = taskService.aiProcessingTask(id);
        return ResponseEntity.ok(task);
    }
}
