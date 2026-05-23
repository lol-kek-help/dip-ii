package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.model.Status;
import com.example.giga_test.integration.LlmClient;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiService {
    private final LlmClient llmClient;
    private final TaskRepository taskRepository;
    private final KnowledgeBaseArticleRepository articleRepository;
    private final EmbeddingService embeddingService;

    public AiService(LlmClient llmClient, TaskRepository taskRepository, KnowledgeBaseArticleRepository articleRepository, EmbeddingService embeddingService) {
        this.llmClient = llmClient;
        this.taskRepository = taskRepository;
        this.articleRepository = articleRepository;
        this.embeddingService = embeddingService;
    }

    public ClassifyResponse classify(String text) {
        String lc = text.toLowerCase();
        String category = lc.contains("доступ") ? "ACCESS" : lc.contains("инцид") ? "INCIDENT" : "GENERAL";
        String priority = lc.contains("крит") || lc.contains("простой") ? "URGENT" : "MEDIUM";
        String llmRaw = llmClient.ask("Верни JSON с полями category и priority для обращения: " + text);
        String llmCategory = extractJsonField(llmRaw, "category");
        String llmPriority = extractJsonField(llmRaw, "priority");
        if (llmCategory != null && !llmCategory.isBlank()) {
            category = llmCategory.toUpperCase(Locale.ROOT);
        }
        if (llmPriority != null && !llmPriority.isBlank()) {
            priority = llmPriority.toUpperCase(Locale.ROOT);
        }
        return new ClassifyResponse(category, priority, llmRaw);
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
        return new SimilarResponse(taskItems, resolvedCases, articles);
    }

    public RecommendResponse recommend(String text) {
        var sim = similar(text);
        String ragPrompt = buildRagPrompt(text, sim);
        return new RecommendResponse(llmClient.ask(ragPrompt),
                List.of("Проверить похожие кейсы: "+sim.resolvedCases().size(), "Определить маршрутизацию на 2-ю линию", "Подтвердить SLA и эскалацию"));
    }

    private double score(String a, String b) {
        Set<String> sa = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\s+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\s+")));
        if (sa.isEmpty()) return 0;
        long common = sa.stream().filter(sb::contains).count();
        return common * 1.0 / sa.size();
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private ResolvedCaseItem mapResolvedCase(Long id, double score, List<TaskEntity> tasks) {
        TaskEntity task = tasks.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        if (task == null) return null;
        return new ResolvedCaseItem(
                task.getId(),
                task.getTitle(),
                roundToTwoDecimals(score * 100.0),
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

    private String extractJsonField(String json, String field) {
        if (json == null) return null;
        String pattern = "\"" + field + "\"";
        int idx = json.toLowerCase(Locale.ROOT).indexOf(pattern.toLowerCase(Locale.ROOT));
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2).trim();
    }
}
