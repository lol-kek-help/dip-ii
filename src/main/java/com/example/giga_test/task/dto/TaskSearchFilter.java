package com.example.giga_test.task.dto;

public record TaskSearchFilter(
        Long assignedTo,
        Long requester,
        Integer pageSize,
        Integer pageNumber
) {
}
