package com.example.giga_test;

import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.integration.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.giga_test.ai.config.AiSearchProperties;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ticket.repository.AiRecommendationRepository;
import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.ai.service.EmbeddingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AiServiceTest {
    @Test
    void classifyShouldReturnCategoryAndPriority() {
        LlmJsonGateway llmJsonGateway = Mockito.mock(LlmJsonGateway.class);
        when(llmJsonGateway.classify(anyString()))
                .thenReturn(new LlmJsonGateway.LlmJsonResult(true, "ACCESS", "HIGH", "mocked", "{\"category\":\"ACCESS\",\"priority\":\"HIGH\"}", "OK"));
        AiService service = new AiService(
                llmJsonGateway,
                Mockito.mock(TaskRepository.class),
                Mockito.mock(KnowledgeBaseArticleRepository.class),
                Mockito.mock(EmbeddingService.class),
                Mockito.mock(JdbcTemplate.class),
                new AiSearchProperties(),
                Mockito.mock(AiRecommendationRepository.class),
                Mockito.mock(AuditLogRepository.class)
        );
        var result = service.classify("критичный инцидент с доступом");
        assertNotNull(result.category());
        assertNotNull(result.priority());
    }

    @Test
    void classifyShouldUseLastValidJsonObjectWhenModelEchoesPromptExamples() {
        LlmJsonGateway gateway = new LlmJsonGateway(fakeClient(
                "Перед ответом пример из prompt: {\"category\":\"ACCESS\",\"priority\":\"MEDIUM\",\"rationale\":\"пример\"} " +
                        "и мусор {}. Итог: {\"category\":\"INCIDENT\",\"priority\":\"URGENT\",\"rationale\":\"критичный простой\"}"
        ), new ObjectMapper());

        var result = gateway.classify("критичный простой сервиса");

        assertTrue(result.valid());
        assertEquals("INCIDENT", result.category());
        assertEquals("URGENT", result.priority());
        assertEquals("критичный простой", result.rationale());
    }

    @Test
    void classifyShouldIgnoreBracesInsideJsonStrings() {
        LlmJsonGateway gateway = new LlmJsonGateway(fakeClient(
                "```json\n{\"category\":\"ACCESS\",\"priority\":\"HIGH\",\"rationale\":\"ошибка содержит {код} внутри текста\"}\n```"
        ), new ObjectMapper());

        var result = gateway.classify("нет доступа");

        assertTrue(result.valid());
        assertEquals("ACCESS", result.category());
        assertEquals("HIGH", result.priority());
        assertEquals("ошибка содержит {код} внутри текста", result.rationale());
    }

    private LlmClient fakeClient(String rawResponse) {
        return new LlmClient() {
            @Override
            public String ask(String prompt) {
                return rawResponse;
            }

            @Override
            public double[] embed(String text) {
                return new double[0];
            }
        };
    }

}
