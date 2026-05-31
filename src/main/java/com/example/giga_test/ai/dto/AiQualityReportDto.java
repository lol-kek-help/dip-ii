package com.example.giga_test.ai.dto;

public record AiQualityReportDto(
        long totalRecommendations,
        long evaluatedRecommendations,
        long acceptedRecommendations,
        double acceptanceRate,
        double averageUsefulnessScore,
        long classificationChanges,
        double classificationChangeRate
) {}
