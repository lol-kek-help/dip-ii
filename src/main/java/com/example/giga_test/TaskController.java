package com.example.giga_test;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

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

    @GetMapping("/")
    public ResponseEntity<List<Task>> getAllTask() {
        log.info("called getAllTask");
        return ResponseEntity.ok(taskService.findAllTask());
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
