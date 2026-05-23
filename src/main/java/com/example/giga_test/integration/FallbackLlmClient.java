package com.example.giga_test.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FallbackLlmClient implements LlmClient {
    @Override
    public String ask(String prompt) {
        return "AI service temporary unavailable; use manual triage.";
    }
}
