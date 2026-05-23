package com.example.giga_test.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "ai.llm", name = "enabled", havingValue = "true")
public class GigaChatLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GigaChatLlmClient.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String accessToken;
    private final String model;
    private final double temperature;

    public GigaChatLlmClient(
            @Value("${ai.llm.gigachat.api-url:https://gigachat.devices.sberbank.ru/api/v1/chat/completions}") String apiUrl,
            @Value("${ai.llm.gigachat.access-token:}") String accessToken,
            @Value("${ai.llm.gigachat.model:GigaChat-Pro}") String model,
            @Value("${ai.llm.gigachat.temperature:0.2}") double temperature) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = apiUrl;
        this.accessToken = accessToken;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String ask(String prompt) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("AI включен, но ai.llm.gigachat.access-token пустой. Возвращаем деградированный ответ.");
            return "AI client misconfigured: empty GigaChat access token.";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", temperature,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, request, Map.class);

            return extractContent(response.getBody());
        } catch (RestClientException ex) {
            log.error("Ошибка вызова GigaChat API", ex);
            return "AI service temporary unavailable; use manual triage.";
        }
    }

    private String extractContent(Map<?, ?> body) {
        if (body == null) {
            return "AI returned empty response.";
        }

        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return "AI returned no choices.";
        }

        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return "AI returned invalid choice format.";
        }

        Object messageObj = firstMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return "AI returned invalid message format.";
        }

        Object content = messageMap.get("content");
        return content == null ? "AI returned empty message." : content.toString();
    }
}
