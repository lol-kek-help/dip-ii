package com.example.giga_test.mapper;

import com.example.giga_test.entity.TaskEntity;
import com.example.giga_test.model.Task;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {
    public Task entityToTask(TaskEntity entity) {
        Task task = new Task();
        BeanUtils.copyProperties(entity, task);
        task.setDescriprion(entity.getDescription());
        return task;
    }

    public TaskEntity taskToEntity(Task task) {
        TaskEntity entity = new TaskEntity();
        BeanUtils.copyProperties(task, entity);
        entity.setDescription(task.getDescriprion());
        return entity;
    }
}
