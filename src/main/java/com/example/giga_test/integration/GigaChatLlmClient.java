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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "ai.llm", name = "enabled", havingValue = "true")
public class GigaChatLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GigaChatLlmClient.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String authUrl;
    private final String embeddingsUrl;
    private final String authKey;
    private final String scope;
    private final String model;
    private final String embeddingsModel;
    private final double temperature;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt;
    private volatile Instant throttleUntil;

    public GigaChatLlmClient(
            @Value("${ai.llm.gigachat.api-url:https://gigachat.devices.sberbank.ru/api/v1/chat/completions}") String apiUrl,
            @Value("${ai.llm.gigachat.auth-url:https://ngw.devices.sberbank.ru:9443/api/v2/oauth}") String authUrl,
            @Value("${ai.llm.gigachat.embeddings-url:https://gigachat.devices.sberbank.ru/api/v1/embeddings}") String embeddingsUrl,
            @Value("${ai.llm.gigachat.auth-key:}") String authKey,
            @Value("${ai.llm.gigachat.scope:GIGACHAT_API_PERS}") String scope,
            @Value("${ai.llm.gigachat.model:GigaChat-Pro}") String model,
            @Value("${ai.llm.gigachat.embeddings-model:Embeddings}") String embeddingsModel,
            @Value("${ai.llm.gigachat.temperature:0.2}") double temperature) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = apiUrl;
        this.authUrl = authUrl;
        this.embeddingsUrl = embeddingsUrl;
        this.authKey = authKey;
        this.scope = scope;
        this.model = model;
        this.embeddingsModel = embeddingsModel;
        this.temperature = temperature;
    }

    @Override
    public double[] embed(String text) {
        if (authKey == null || authKey.isBlank()) {
            return new double[0];
        }
        if (isThrottled()) {
            return new double[0];
        }
        try {
            String accessToken = getOrRefreshAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            Map<String, Object> body = Map.of(
                    "model", embeddingsModel,
                    "input", List.of(text)
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(embeddingsUrl, HttpMethod.POST, request, Map.class);
            return extractEmbedding(response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 429) {
                activateThrottle();
            }
            log.warn("Не удалось получить embedding из GigaChat, используем локальный fallback", ex);
            return new double[0];
        } catch (ResourceAccessException ex) {
            activateThrottle();
            log.warn("Нет сети/доступа к GigaChat embeddings, используем локальный fallback: {}", ex.getMessage());
            return new double[0];
        } catch (RestClientException ex) {
            log.warn("Не удалось получить embedding из GigaChat, используем локальный fallback", ex);
            return new double[0];
        }
    }

    @Override
    //запрос llm
    public String ask(String prompt) {
        if (authKey == null || authKey.isBlank()) {
            log.warn("AI включен, но ai.llm.gigachat.auth-key пустой. Возвращаем деградированный ответ.");
            return "AI client misconfigured: empty GigaChat auth key.";
        }
        try {
            String accessToken = getOrRefreshAccessToken();

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
            //fallback ответ
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            log.error("Ошибка вызова GigaChat API. HTTP status={}", status, ex);
            if (status == 429) {
                activateThrottle();
                return "Квота GigaChat исчерпана (HTTP 429).";
            }
            return "AI service temporary unavailable; use manual triage.";
        } catch (ResourceAccessException ex) {
            activateThrottle();
            log.warn("Нет сети/доступа к GigaChat API, используем fallback: {}", ex.getMessage());
            return "AI недоступен (проблема сети/DNS), используйте ручную обработку.";
        } catch (RestClientException ex) {
            log.error("Ошибка вызова GigaChat API", ex);
            return "AI service temporary unavailable; use manual triage.";
        }
    }

    private synchronized String getOrRefreshAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && tokenExpiresAt != null && now.isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedAccessToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("RqUID", UUID.randomUUID().toString());
        headers.set("Authorization", "Basic " + authKey.trim());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("scope", scope);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        ResponseEntity<Map> response = restTemplate.exchange(authUrl, HttpMethod.POST, request, Map.class);
        Map<?, ?> body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new RestClientException("OAuth ответ GigaChat не содержит access_token");
        }

        cachedAccessToken = body.get("access_token").toString();
        tokenExpiresAt = resolveExpiry(body, now);
        return cachedAccessToken;
    }

    private Instant resolveExpiry(Map<?, ?> body, Instant now) {
        Object expiresAtRaw = body.get("expires_at");
        if (expiresAtRaw != null) {
            try {
                long millis = Long.parseLong(expiresAtRaw.toString());
                return Instant.ofEpochMilli(millis);
            } catch (NumberFormatException ignored) {
                log.warn("Не удалось разобрать expires_at='{}', используем fallback 25 минут", expiresAtRaw);
            }
        }

        Object expiresInRaw = body.get("expires_in");
        if (expiresInRaw != null) {
            try {
                long seconds = Long.parseLong(expiresInRaw.toString());
                return now.plusSeconds(seconds);
            } catch (NumberFormatException ignored) {
                log.warn("Не удалось разобрать expires_in='{}', используем fallback 25 минут", expiresInRaw);
            }
        }

        return now.plusSeconds(25 * 60L);
    }

    private boolean isThrottled() {
        Instant until = throttleUntil;
        return until != null && Instant.now().isBefore(until);
    }

    private void activateThrottle() {
        this.throttleUntil = Instant.now().plusSeconds(30);
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

    private double[] extractEmbedding(Map<?, ?> body) {
        if (body == null) return new double[0];
        Object dataObj = body.get("data");
        if (!(dataObj instanceof List<?> data) || data.isEmpty()) return new double[0];
        Object first = data.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return new double[0];
        Object embObj = firstMap.get("embedding");
        if (!(embObj instanceof List<?> embList) || embList.isEmpty()) return new double[0];
        double[] vec = new double[embList.size()];
        for (int i = 0; i < embList.size(); i++) {
            Object v = embList.get(i);
            if (v instanceof Number n) vec[i] = n.doubleValue();
        }
        return vec;
    }
}
