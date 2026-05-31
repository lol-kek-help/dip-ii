package com.example.giga_test.common.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {}
