package com.example.giga_test.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AiDtos {
    public record AiRequest(@NotBlank String text) {}

    public record Explainability(String mode, List<String> sources, String llmStatus, String rawModelOutput, String fallbackReason) {}

    public record ClassifyResponse(String category, String priority, String rationale, Explainability explainability) {}
    public record SimilarItem(Long ticketId, String title, double score) {}
    public record ResolvedCaseItem(Long ticketId, String title, double fitPercent, String resolutionComment) {}
    public record SimilarResponse(List<SimilarItem> tickets, List<ResolvedCaseItem> resolvedCases, List<String> articles, Explainability explainability) {}
    public record RecommendResponse(String recommendation, List<String> steps, Explainability explainability) {}
}
