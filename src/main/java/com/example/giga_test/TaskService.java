package com.example.giga_test;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskService {

    private final Map<Long, Task> taskMap;
    private final AtomicLong idCounter;
    private final TaskRepository repository;
    public TaskService(TaskRepository repository) {
        this.repository = repository;
        taskMap = new HashMap<>();
        idCounter = new AtomicLong();
    }

    public List<Task> findAllTask() {
        List<TaskEntity> allEntitys = repository.findAll();
        return allEntitys.stream()
                .map(entity -> {
                    Task task = new Task();
                    BeanUtils.copyProperties(entity, task);
                    return task;
                })
                .toList();

    }

    public Task getTaskByID(
            Long id
    ) {
        if (!taskMap.containsKey(id)){
            throw new NoSuchElementException("No that id = " + id);
        }
        return taskMap.get(id);
    }

    public Task createTask(Task taskToCreate) {
        Task newTask = taskToCreate.toBuilder()
                .id(idCounter.incrementAndGet())
                .build();
        taskMap.put(newTask.getId(), newTask);
        return newTask;
    }

    public Task updateTask(Long id, Task taskToUpdate) {
        if (!taskMap.containsKey(id)){
            throw new NoSuchElementException("No that id = " + id);
        }
        var task = taskMap.get(id);
        if(task.status == Status.CLOSED){
            throw new IllegalStateException("Cannot modify" + task.status);
        }
        Task updatetTask = taskToUpdate.toBuilder().build();
        taskMap.put(updatetTask.getId(), updatetTask);
        return updatetTask;
    }

    public void deleteTask(Long id) {
        if (!taskMap.containsKey(id)){
            throw new NoSuchElementException("No that id = " + id);
        }
        taskMap.remove(id);
    }

    public Task aiProcessingTask(Long id) {
        if (!taskMap.containsKey(id)){
            throw new NoSuchElementException("No that id = " + id);
        }
        var task = taskMap.get(id);
        if(task.status == Status.CLOSED){
            throw new IllegalStateException("Cannot approve" + task.status);
        }
        task = sendToAI(task);
        return null;
    }
    private Task sendToAI (Task task){ //TODO: AI
        task.setCategory(Category.INCIDENT);
        return task;
    }
}
