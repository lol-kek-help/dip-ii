package com.example.giga_test;

import com.example.giga_test.ai.dto.AiDtos;
import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.integration.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.giga_test.ai.config.AiSearchProperties;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.ai.repository.VectorRecordRepository;
import com.example.giga_test.ai.entity.VectorRecord;
import com.example.giga_test.ai.entity.KnowledgeBaseArticle;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ticket.repository.AiRecommendationRepository;
import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.ai.service.EmbeddingService;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

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

    @Test
    void localEmbeddingFallbackShouldMatchSynonyms() {
        VectorRecordRepository vectorRecordRepository = Mockito.mock(VectorRecordRepository.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        EmbeddingService service = new EmbeddingService(vectorRecordRepository, fakeClient(""), jdbcTemplate);

        double[] emailVector = service.embed("Проблема с электронной почтой Outlook");
        VectorRecord emailRecord = VectorRecord.builder()
                .id(1L)
                .sourceType("TASK")
                .sourceId(100L)
                .textContent("Проблема с электронной почтой Outlook")
                .embedding(serialize(emailVector))
                .embeddingProvider("LOCAL_HASH:SYN_V1")
                .embeddingDimension(emailVector.length)
                .build();

        doThrow(new DataAccessResourceFailureException("pgvector unavailable"))
                .when(jdbcTemplate)
                .query(anyString(), ArgumentMatchers.<RowMapper<EmbeddingService.ScoredVectorRecord>>any(), any(), any(), any(), any(), anyInt());
        when(vectorRecordRepository.findAllBySourceTypeAndEmbeddingProvider("TASK", "LOCAL_HASH:SYN_V1"))
                .thenReturn(List.of(emailRecord));

        var result = service.topK("TASK", "email не работает", 1);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).record().getSourceId());
        assertTrue(result.get(0).score() > 0.15);
    }

    @Test
    void recommendShouldPreferKnowledgeBaseContentOverGenericLlmAdvice() {
        LlmJsonGateway llmJsonGateway = Mockito.mock(LlmJsonGateway.class);
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        KnowledgeBaseArticleRepository articleRepository = Mockito.mock(KnowledgeBaseArticleRepository.class);
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        KnowledgeBaseArticle article = new KnowledgeBaseArticle(
                7L,
                "Взорвалась машина",
                "Если у вас взорвалась машина, немедленно начните танцевать и кричать \"ураааааа\".",
                "GENERAL",
                null,
                null,
                "system",
                "system"
        );
        VectorRecord articleVector = VectorRecord.builder()
                .id(7L)
                .sourceType("KB")
                .sourceId(7L)
                .textContent(article.getTitle() + " " + article.getContent())
                .embedding("0.1,0.2")
                .embeddingProvider("LOCAL_HASH:SYN_V1")
                .embeddingDimension(2)
                .build();

        when(embeddingService.topK("TASK", "Взорвалась машина\nкапец взорвалась", 25)).thenReturn(List.of());
        when(embeddingService.topK("KB", "Взорвалась машина\nкапец взорвалась", 25))
                .thenReturn(List.of(new EmbeddingService.ScoredVectorRecord(articleVector, 0.9)));
        when(taskRepository.findAllById(any())).thenReturn(List.of());
        when(articleRepository.findAllById(any())).thenReturn(List.of(article));
        doReturn(List.of()).when(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<AiDtos.SimilarItem>>any(), any(), any(), any());
        when(llmJsonGateway.rerankTickets(anyString(), any())).thenReturn(java.util.Map.of());
        when(llmJsonGateway.recommend(anyString())).thenReturn("Общая рекомендация: отойдите в безопасное место и позвоните 112.");

        AiService service = new AiService(
                llmJsonGateway,
                taskRepository,
                articleRepository,
                embeddingService,
                jdbcTemplate,
                new AiSearchProperties(),
                Mockito.mock(AiRecommendationRepository.class),
                Mockito.mock(AuditLogRepository.class)
        );

        var response = service.recommend("Взорвалась машина\nкапец взорвалась");

        assertTrue(response.recommendation().contains("танцевать"));
        assertTrue(response.recommendation().contains("ураааааа"));
        assertEquals("RAG_GROUNDED", response.explainability().mode());
        assertTrue(response.steps().get(0).contains("танцевать"));
    }

    private String serialize(double[] vec) {
        return Arrays.stream(vec).mapToObj(Double::toString).reduce((a, b) -> a + "," + b).orElse("");
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
