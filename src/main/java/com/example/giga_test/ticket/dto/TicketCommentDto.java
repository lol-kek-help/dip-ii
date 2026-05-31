package com.example.giga_test.ticket.dto;

import com.example.giga_test.user.dto.UserSummaryDto;

import java.time.LocalDateTime;

public record TicketCommentDto(
        Long id,
        Long ticketId,
        UserSummaryDto author,
        String commentText,
        boolean internalComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
