package com.example.giga_test.task.dto;

import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        Object priority,
        Object category,
        Object status,
        Object requesterId,
        Object assigneeId,
        Object resolutionDeadline,
        String resolutionComment
) {}
