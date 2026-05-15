package com.example.giga_test.service;

public record TaskSearchFilter(
        Long assignedTo,
        Long requester,
        Integer pageSize,
        Integer pageNumber
) {
}
