package com.example.giga_test.exceptions;

import java.time.LocalDateTime;

public record ErrorResponseDto(
        String code,
        String message,
        String detailedMessage,
        LocalDateTime errorTime
) {
}
