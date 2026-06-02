package com.example.giga_test.ticket.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SaveAiRecommendationRequest(
        @NotBlank String recommendation,
        List<String> steps,
        String mode,
        List<String> sources,
        String llmStatus,
        String rawModelOutput
) {}
