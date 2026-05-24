package com.example.giga_test.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.giga_test.integration.LlmClient;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LlmJsonGateway {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmJsonGateway(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public LlmJsonResult classify(String text) {
        String prompt = "Верни строго JSON без markdown: {\"category\":\"ACCESS|INCIDENT|GENERAL\",\"priority\":\"LOW|MEDIUM|HIGH|URGENT\",\"rationale\":\"...\"}. Обращение: " + text;
        String raw = llmClient.ask(prompt);
        try {
            JsonNode root = objectMapper.readTree(raw);
            validate(root, Set.of("category", "priority", "rationale"));
            String category = root.get("category").asText();
            String priority = root.get("priority").asText();
            if (!Set.of("ACCESS", "INCIDENT", "GENERAL").contains(category)) throw new IllegalArgumentException("category out of contract");
            if (!Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(priority)) throw new IllegalArgumentException("priority out of contract");
            return new LlmJsonResult(true, category, priority, root.get("rationale").asText(), raw, "OK");
        } catch (Exception ex) {
            return new LlmJsonResult(false, null, null, null, raw, "INVALID_JSON_CONTRACT");
        }
    }

    public String recommend(String prompt) {
        return llmClient.ask(prompt);
    }

    private void validate(JsonNode root, Set<String> required) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("root must be object");
        for (String field : required) {
            if (!root.hasNonNull(field)) throw new IllegalArgumentException("missing field " + field);
            if (!root.get(field).isTextual()) throw new IllegalArgumentException("field must be text " + field);
        }
    }

    public record LlmJsonResult(boolean valid, String category, String priority, String rationale, String raw, String status) {}
}
