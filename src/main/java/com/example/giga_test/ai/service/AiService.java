package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.model.Status;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiService {
    private final LlmJsonGateway llmJsonGateway;
    private final TaskRepository taskRepository;
    private final KnowledgeBaseArticleRepository articleRepository;
    private final EmbeddingService embeddingService;

    public AiService(LlmJsonGateway llmJsonGateway, TaskRepository taskRepository, KnowledgeBaseArticleRepository articleRepository, EmbeddingService embeddingService) {
        this.llmJsonGateway = llmJsonGateway;
        this.taskRepository = taskRepository;
        this.articleRepository = articleRepository;
        this.embeddingService = embeddingService;
    }

    public ClassifyResponse classify(String text) {
        String lc = text.toLowerCase(Locale.ROOT);
        String fallbackCategory = lc.contains("доступ") ? "ACCESS" : lc.contains("инцид") ? "INCIDENT" : "GENERAL";
        String fallbackPriority = lc.contains("крит") || lc.contains("простой") ? "URGENT" : "MEDIUM";

        var llm = llmJsonGateway.classify(text);
        boolean useLlm = llm.valid();
        String category = useLlm ? llm.category() : fallbackCategory;
        String priority = useLlm ? llm.priority() : fallbackPriority;
        String rationale = useLlm ? llm.rationale() : "Fallback rule-based classification due to unavailable/invalid LLM response.";

        return new ClassifyResponse(category, priority, rationale,
                new Explainability(useLlm ? "LLM_JSON" : "FALLBACK_RULES", List.of("ticket_text"), llm.status(), llm.raw()));
    }

    public SimilarResponse similar(String text) {
        var taskItems = taskRepository.findTop5ByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(text, text).stream()
                .map(t -> new SimilarItem(t.getId(), t.getTitle(), score(text, t.getTitle()+" "+t.getDescription())))
                .sorted(Comparator.comparingDouble(SimilarItem::score).reversed()).toList();

        var resolvedTasks = taskRepository.findAllByStatus(Status.RESOLVED);
        resolvedTasks.forEach(embeddingService::upsertTaskEmbedding);

        var resolvedCases = embeddingService.topK("TASK", text, 3).stream()
                .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), resolvedTasks))
                .filter(Objects::nonNull)
                .toList();

        var kbArticles = articleRepository.findAll();
        kbArticles.forEach(a -> embeddingService.upsertKnowledgeEmbedding(a.getId(), a.getTitle() + " " + a.getContent()));
        var articles = embeddingService.topK("KB", text, 3).stream()
                .map(scored -> kbArticles.stream().filter(a -> a.getId().equals(scored.record().getSourceId())).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .map(a -> a.getTitle() + " (релевантность: " + roundToTwoDecimals(100.0 * score(text, a.getTitle() + " " + a.getContent())) + "%)")
                .toList();

        return new SimilarResponse(taskItems, resolvedCases, articles,
                new Explainability("RAG_RETRIEVAL", List.of("resolved_tickets", "knowledge_base", "vector_records"), "N/A", null));
    }

    public RecommendResponse recommend(String text) {
        var sim = similar(text);
        String ragPrompt = buildRagPrompt(text, sim);
        String recommendation = llmJsonGateway.recommend(ragPrompt);
        return new RecommendResponse(recommendation,
                List.of("Проверить похожие кейсы: "+sim.resolvedCases().size(), "Определить маршрутизацию на 2-ю линию", "Подтвердить SLA и эскалацию"),
                new Explainability("RAG_PLUS_LLM", List.of("resolved_tickets", "knowledge_base", "vector_records"), "OK_OR_DEGRADED", recommendation));
    }

    private double score(String a, String b) {
        Set<String> sa = new HashSet<>(Arrays.asList(a.toLowerCase(Locale.ROOT).split("\\s+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.toLowerCase(Locale.ROOT).split("\\s+")));
        if (sa.isEmpty()) return 0;
        long common = sa.stream().filter(sb::contains).count();
        return common * 1.0 / sa.size();
    }

    private double roundToTwoDecimals(double value) { return Math.round(value * 100.0) / 100.0; }

    private ResolvedCaseItem mapResolvedCase(Long id, double score, List<TaskEntity> tasks) {
        TaskEntity task = tasks.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        if (task == null) return null;
        return new ResolvedCaseItem(task.getId(), task.getTitle(), roundToTwoDecimals(score * 100.0),
                task.getResolutionComment() == null || task.getResolutionComment().isBlank() ? "Решение не заполнено" : task.getResolutionComment());
    }

    private String buildRagPrompt(String text, SimilarResponse sim) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты помощник техподдержки. Сформируй краткую рекомендацию по обращению: ").append(text).append("\n\n");
        sb.append("Похожие решенные кейсы:\n");
        sim.resolvedCases().forEach(c -> sb.append("- ").append(c.title()).append(" [").append(c.fitPercent()).append("%]: ").append(c.resolutionComment()).append("\n"));
        sb.append("\nРелевантные статьи БЗ:\n");
        sim.articles().forEach(a -> sb.append("- ").append(a).append("\n"));
        sb.append("\nДополнительно верни маршрутизацию (очередь/группа) и первые 3 действия.");
        return sb.toString();
    }
}
