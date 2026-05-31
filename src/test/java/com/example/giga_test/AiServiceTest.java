package com.example.giga_test;

import com.example.giga_test.ai.integration.LlmJsonGateway;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AiServiceTest {
    @Test
    void classifyShouldReturnCategoryAndPriority() {
        LlmJsonGateway llmJsonGateway = Mockito.mock(LlmJsonGateway.class);
        when(llmJsonGateway.classify(anyString()))
                .thenReturn(new LlmJsonGateway.LlmJsonResult(true, "ACCESS", "HIGH", "mocked", "OK", "{\"category\":\"ACCESS\",\"priority\":\"HIGH\"}"));
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
}
