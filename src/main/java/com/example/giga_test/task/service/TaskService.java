package com.example.giga_test.task.service;

import com.example.giga_test.task.dto.TaskSearchFilter;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.mapper.TaskMapper;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.Task;
import com.example.giga_test.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskMapper mapper;
    private final TaskRepository repository;
    public TaskService(TaskMapper mapper, TaskRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    public List<Task> searchTaskByFilter(
            TaskSearchFilter filter
    ) {
        int pageSize = filter.pageSize() != null
                ? filter.pageSize() : 10;
        int pageNumber = filter.pageNumber() != null
                ? filter.pageNumber() : 0;
        var pageable = Pageable.ofSize(pageSize).withPage(pageNumber);
        Page<TaskEntity> allEntitys = repository.searchByFilter(
                filter.requester(),
                filter.assignedTo(),
                pageable
        );
        return allEntitys.stream()
                .map(mapper::entityToTask)
                .toList();

    }
    public List<Task> findAllTask() { //переписано для бд
        List<TaskEntity> allEntitys = repository.findAll();
        return allEntitys.stream()
                .map(mapper::entityToTask)
                .toList();

    }

    public Task getTaskByID(//переписано для бд
            Long id
    ) {
        TaskEntity taskEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No that id = " + id
                ));

        return mapper.entityToTask(taskEntity);
    }

    public Task createTask(Task taskToCreate) {//переписано для бд
        if (!taskToCreate.getResolutionDeadline().isAfter(
                taskToCreate.getCreatedAt()
        )){
            throw new IllegalArgumentException("До дедлайна минимум должен быть 1 день");
        }
        TaskEntity entityToSave = mapper.taskToEntity(taskToCreate);
        entityToSave.setId(null);

        TaskEntity savedEntity = repository.save(entityToSave);
        return mapper.entityToTask(savedEntity);
    }

    public Task updateTask(Long id, Task taskToUpdate) {
        TaskEntity taskEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No that id = " + id
                ));
        if(taskEntity.getStatus() == Status.CLOSED){
            throw new IllegalStateException("Cannot modify" + taskEntity.getStatus());
        }
        if (!taskToUpdate.getResolutionDeadline().isAfter(
                taskToUpdate.getCreatedAt()
        )){
            throw new IllegalArgumentException("До дедлайна минимум должен быть 1 день");
        }
        TaskEntity entityToUpdate = mapper.taskToEntity(taskToUpdate);
        entityToUpdate.setId(id);

        TaskEntity updatedEntity = repository.save(entityToUpdate);
        return mapper.entityToTask(updatedEntity);
    }
    @Transactional
    public void cancelTask(Long id) {
        TaskEntity taskEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No that id = " + id
                ));
        if (taskEntity.getStatus().equals(Status.CLOSED) ||
                taskEntity.getStatus().equals(Status.CANCELED) ||
                taskEntity.getStatus().equals(Status.RESOLVED)
        ){
            throw new IllegalStateException("Нельзя отменить задачу со статусом " + taskEntity.getStatus());
        }
        repository.setStatus(id, Status.CANCELED);
        log.info("Success cancel task id: " + id);
    }

    public Task aiProcessingTask(Long id) {
        TaskEntity taskEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No that id = " + id
                ));
        if(taskEntity.getStatus() == Status.CLOSED){
            throw new IllegalStateException
                    ("Cannot approve" + taskEntity.getStatus());
        }
        Task processedTask = sendToAI(mapper.entityToTask(taskEntity));

        TaskEntity entityToSave = mapper.taskToEntity(processedTask);
        entityToSave.setId(taskEntity.getId());

        TaskEntity savedEntity = repository.save(entityToSave);
        return mapper.entityToTask(savedEntity);
    }
    private Task sendToAI(Task task) { //TODO: AI
        // 1. Собрать prompt из task.title, task.description, task.priority
        // 2. Отправить prompt в GigaChat
        // 3. Получить ответ
        // 4. На основании ответа изменить category/status/resolutionComment
        // 5. Вернуть обновленный Task
        return task.toBuilder()
                .category(Category.INCIDENT)
                .build();
    }

}
