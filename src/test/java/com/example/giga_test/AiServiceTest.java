package com.example.giga_test;

import com.example.giga_test.integration.LlmClient;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ai.service.AiService;
import com.example.giga_test.ai.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AiServiceTest {
    @Test
    void classifyShouldReturnCategoryAndPriority() {
        LlmClient llm = Mockito.mock(LlmClient.class);
        when(llm.ask(anyString())).thenReturn("ok");
        AiService service = new AiService(llm, Mockito.mock(TaskRepository.class), Mockito.mock(KnowledgeBaseArticleRepository.class), Mockito.mock(EmbeddingService.class));
        var result = service.classify("критичный инцидент с доступом");
        assertNotNull(result.category());
        assertNotNull(result.priority());
    }
}
