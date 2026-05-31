package com.example.giga_test.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketCommentRequest(
        @NotBlank @Size(max = 4000) String commentText,
        boolean internalComment
) {}
