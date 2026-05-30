package com.example.giga_test.exceptions;

import java.time.LocalDateTime;
//единый формат ответа с ошибкой
public record ErrorResponseDto(
        String code,
        String message,
        String detailedMessage,
        LocalDateTime errorTime
) {}
