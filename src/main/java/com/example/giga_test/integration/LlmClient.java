package com.example.giga_test.integration;

public interface LlmClient {
    String ask(String prompt);
    double[] embed(String text);

    default String embeddingProviderKey() {
        return "LOCAL_HASH";
    }
}
