package com.example.giga_test.ticket.dto;

import com.example.giga_test.model.Status;
import com.example.giga_test.user.dto.UserSummaryDto;

import java.time.LocalDateTime;

public record TicketStatusHistoryDto(
        Long id,
        Long ticketId,
        Status fromStatus,
        Status toStatus,
        String reason,
        UserSummaryDto changedBy,
        LocalDateTime createdAt
) {}
