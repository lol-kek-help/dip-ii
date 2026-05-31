package com.example.giga_test.ticket.dto;

import com.example.giga_test.user.dto.UserSummaryDto;

import java.time.LocalDateTime;
import java.util.List;

public record SavedAiRecommendationDto(
        Long id,
        Long ticketId,
        String recommendation,
        List<String> steps,
        String mode,
        List<String> sources,
        String llmStatus,
        String rawModelOutput,
        Boolean accepted,
        Integer usefulnessScore,
        String feedbackComment,
        UserSummaryDto createdBy,
        UserSummaryDto evaluatedBy,
        LocalDateTime createdAt,
        LocalDateTime evaluatedAt
) {}
