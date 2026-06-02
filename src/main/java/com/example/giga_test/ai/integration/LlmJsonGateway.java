package com.example.giga_test.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.giga_test.integration.LlmClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;

@Component
public class LlmJsonGateway {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmJsonGateway(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public LlmJsonResult classify(String text) {
        String prompt = """
                Ты классификатор заявок техподдержки.
                Верни ТОЛЬКО JSON-объект без markdown и без пояснений.
                category: одно из [ACCESS, INCIDENT, GENERAL]
                priority: одно из [LOW, MEDIUM, HIGH, URGENT]
                rationale: краткое объяснение на русском языке.
                Предложи коротко какой команде технической поддержки следует маршрутизировать.
                Формат ответа:
                {"category":"ACCESS","priority":"MEDIUM","rationale":"..."}
                Текст обращения:
                """ + text;
        String raw = llmClient.ask(prompt);
        for (String candidate : extractJsonObjectCandidates(raw)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                validate(root, Set.of("category", "priority", "rationale"));
                String category = root.get("category").asText();
                String priority = root.get("priority").asText();
                if (!Set.of("ACCESS", "INCIDENT", "GENERAL").contains(category)) throw new IllegalArgumentException("category out of contract");
                if (!Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(priority)) throw new IllegalArgumentException("priority out of contract");
                return new LlmJsonResult(true, category, priority, root.get("rationale").asText(), raw, "OK");
            } catch (Exception ignored) {
                // TODO: исправить {}
            }
        }
        return new LlmJsonResult(false, null, null, null, raw, fallbackStatus(raw));
    }

    public String recommend(String prompt) {
        return llmClient.ask(prompt);
    }
    //переоценка похожих случаев
    public Map<Long, Double> rerankTickets(String query, List<CandidateForRerank> candidates) {
        if (candidates == null || candidates.isEmpty()) return Map.of();
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                Ты ранжировщик похожих инцидентов.
                Верни ТОЛЬКО JSON-массив без markdown.
                Формат элемента: {"ticketId":123,"score":0.0}
                score в диапазоне [0,1], больше = релевантнее.
                Оценивай то, насколько кандидат близок к запросу пользователя.
                Учитывай синонимы и общий смысл.
                Верни максимум 10 элементов.
                Запрос пользователя:
                """).append(query).append("\nКандидаты:\n");
        // добавление кандидатов
        for (CandidateForRerank c : candidates) {
            prompt.append("- ticketId=").append(c.ticketId())
                    .append("; title=").append(c.title())
                    .append("; summary=").append(c.summary()).append("\n");
        }
        // валидация и построение Map<Long, Double>
        try {
            String raw = llmClient.ask(prompt.toString());
            JsonNode root = objectMapper.readTree(extractJsonObject(raw).replaceAll("^\\{\\s*\"items\"\\s*:\\s*", "[").replaceAll("\\}\\s*$", "]"));
            if (!root.isArray()) return Map.of();
            Map<Long, Double> out = new HashMap<>();
            for (JsonNode n : root) {
                if (n.hasNonNull("ticketId") && n.hasNonNull("score")) {
                    long id = n.get("ticketId").asLong();
                    double score = Math.max(0.0, Math.min(1.0, n.get("score").asDouble()));
                    out.put(id, score);
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String fallbackStatus(String raw) {
        String normalized = raw == null ? "" : raw.toLowerCase();
        if (normalized.contains("429") || normalized.contains("квота") || normalized.contains("too many")) {
            return "RATE_LIMIT";
        }
        if (normalized.contains("сети") || normalized.contains("dns") || normalized.contains("network")) {
            return "NETWORK_UNAVAILABLE";
        }
        if (normalized.contains("misconfigured") || normalized.contains("auth key")) {
            return "MISCONFIGURED";
        }
        if (normalized.contains("temporary unavailable") || normalized.contains("manual triage") || normalized.contains("недоступ")) {
            return "LLM_UNAVAILABLE";
        }
        return "INVALID_JSON_CONTRACT";
    }

    private void validate(JsonNode root, Set<String> required) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("root must be object");
        for (String field : required) {
            if (!root.hasNonNull(field)) throw new IllegalArgumentException("missing field " + field);
            if (!root.get(field).isTextual()) throw new IllegalArgumentException("field must be text " + field);
        }
    }

    private String extractJsonObject(String raw) {
        List<String> candidates = extractJsonObjectCandidates(raw);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return raw == null ? "" : raw.trim();
    }

    private List<String> extractJsonObjectCandidates(String raw) {
        if (raw == null) {
            return List.of();
        }

        String text = raw.trim();
        List<String> candidates = new ArrayList<>();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                continue;
            }

            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    candidates.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }

        if (candidates.isEmpty()) {
            return List.of(text);
        }

        Collections.reverse(candidates);
        return candidates;
    }

    public record LlmJsonResult(boolean valid, String category, String priority, String rationale, String raw, String status) {}
    public record CandidateForRerank(Long ticketId, String title, String summary) {}
}
