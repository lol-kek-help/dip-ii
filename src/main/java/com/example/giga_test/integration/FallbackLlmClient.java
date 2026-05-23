package com.example.giga_test.integration;

import org.springframework.stereotype.Component;

@Component
public class FallbackLlmClient implements LlmClient {
    @Override
    public String ask(String prompt) {
        return "AI service temporary unavailable; use manual triage.";
    }
}
