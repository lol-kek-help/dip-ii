package com.example.giga_test.task.dto;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;

import java.time.LocalDateTime;

public record TaskSearchFilter(
        Long assignedTo,
        Long requester,
        Status status,
        Priority priority,
        Category category,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime deadlineFrom,
        LocalDateTime deadlineTo,
        Integer pageSize,
        Integer pageNumber,
        String sortBy,
        String sortDir
) {
}
