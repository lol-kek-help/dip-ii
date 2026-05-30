package com.example.giga_test.task.dto;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import jakarta.validation.constraints.NotNull;

public record UpdateClassificationRequest(
        @NotNull Category category,
        @NotNull Priority priority
) {}
