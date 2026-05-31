package com.example.giga_test.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiRecommendationFeedbackRequest(
        @NotNull Boolean accepted,
        @Min(1) @Max(5) Integer usefulnessScore,
        @Size(max = 1000) String feedbackComment
) {}
