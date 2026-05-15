package com.example.giga_test;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);


    private final Map<Long, Task> taskMap;
    private final TaskRepository repository;
    public TaskService(TaskRepository repository) {
        this.repository = repository;
        taskMap = new HashMap<>();
    }

    public List<Task> findAllTask() { //переписано для бд
        List<TaskEntity> allEntitys = repository.findAll();
        return allEntitys.stream()
                .map(this::toTask)
                .toList();

    }

    public Task getTaskByID(//переписано для бд
            Long id
    ) {
        TaskEntity taskEntity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No that id = " + id
                ));

        return toTask(taskEntity);
    }

    public Task createTask(Task taskToCreate) {//переписано для бд
        if (!taskToCreate.getResolutionDeadline().isAfter(
                taskToCreate.getCreatedAt()
        )){
            throw new IllegalArgumentException("До дедлайна минимум должен быть 1 день");
        }
        TaskEntity entityToSave = toEntity(taskToCreate);
        entityToSave.setId(null);

        TaskEntity savedEntity = repository.save(entityToSave);
        return toTask(savedEntity);
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
        TaskEntity entityToUpdate = toEntity(taskToUpdate);
        entityToUpdate.setId(id);

        TaskEntity updatedEntity = repository.save(entityToUpdate);
        return toTask(updatedEntity);
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
        Task processedTask = sendToAI(toTask(taskEntity));

        TaskEntity entityToSave = toEntity(processedTask);
        entityToSave.setId(taskEntity.getId());

        TaskEntity savedEntity = repository.save(entityToSave);
        return toTask(savedEntity);
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
    private Task toTask(TaskEntity entity) {
        Task task = new Task();
        BeanUtils.copyProperties(entity, task);
        task.setDescriprion(entity.getDescription());
        return task;
    }

    private TaskEntity toEntity(Task task) {
        TaskEntity entity = new TaskEntity();
        BeanUtils.copyProperties(task, entity);
        entity.setDescription(task.getDescriprion());
        return entity;
    }
}
