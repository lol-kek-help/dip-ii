package com.example.giga_test.task.dto;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateTaskRequest(
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        Priority priority,
        Category category,
        Status status,
        Long requesterId,
        Long assigneeId,
        @Future LocalDateTime resolutionDeadline,
        String resolutionComment
) {}
