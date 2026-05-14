package com.example.giga_test;

import java.time.LocalDateTime;

public record ErrorResponseDto (
    String message,
    String detailedMessage,
    LocalDateTime errorTime
    ){

}
