package com.example.giga_test.task.dto;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        Priority priority,
        Category category,
        Long requesterId,
        @Future LocalDateTime resolutionDeadline
) {}
