package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.model.Status;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {
    private static final double MIN_RELEVANCE_SCORE = 0.15;
    private static final Set<String> BOOST_TOKENS = Set.of("vpn", "парол", "доступ", "ad");
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
        if (taskItems.isEmpty()) {
            taskItems = taskRepository.findAll().stream()
                    .map(t -> new SimilarItem(t.getId(), t.getTitle(), score(text, t.getTitle() + " " + t.getDescription())))
                    .filter(i -> i.score() > 0)
                    .sorted(Comparator.comparingDouble(SimilarItem::score).reversed())
                    .limit(5)
                    .toList();
        }

        var resolvedTasks = taskRepository.findAllByStatus(Status.RESOLVED);
        resolvedTasks.forEach(embeddingService::upsertTaskEmbedding);

        var resolvedCases = embeddingService.topK("TASK", text, 12).stream()
                .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), text, resolvedTasks))
                .filter(Objects::nonNull)
                .filter(rc -> rc.fitPercent() >= MIN_RELEVANCE_SCORE * 100.0)
                .sorted(Comparator.comparingDouble(ResolvedCaseItem::fitPercent).reversed())
                .limit(3)
                .toList();

        var kbArticles = articleRepository.findAll();
        kbArticles.forEach(a -> embeddingService.upsertKnowledgeEmbedding(a.getId(), a.getTitle() + " " + a.getContent()));
        var articleById = kbArticles.stream().collect(Collectors.toMap(a -> a.getId(), a -> a));
        var articles = embeddingService.topK("KB", text, 12).stream()
                .map(scored -> {
                    var article = articleById.get(scored.record().getSourceId());
                    if (article == null) return null;
                    double boosted = hybridScore(scored.score(), score(text, article.getTitle() + " " + article.getContent()), text, article.getTitle() + " " + article.getContent());
                    if (boosted < MIN_RELEVANCE_SCORE) return null;
                    return article.getTitle() + " (релевантность: " + roundToTwoDecimals(boosted * 100.0) + "%)";
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
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

    private ResolvedCaseItem mapResolvedCase(Long id, double vectorScore, String queryText, List<TaskEntity> tasks) {
        TaskEntity task = tasks.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        if (task == null) return null;
        double lexicalScore = score(queryText, task.getTitle() + " " + task.getDescription());
        double boosted = hybridScore(vectorScore, lexicalScore, queryText, task.getTitle() + " " + task.getDescription());
        return new ResolvedCaseItem(task.getId(), task.getTitle(), roundToTwoDecimals(boosted * 100.0),
                task.getResolutionComment() == null || task.getResolutionComment().isBlank() ? "Решение не заполнено" : task.getResolutionComment());
    }

    private double hybridScore(double vectorScore, double lexicalScore, String queryText, String candidateText) {
        double normalizedVector = Math.max(0.0, Math.min(1.0, vectorScore));
        double base = (0.75 * normalizedVector) + (0.25 * lexicalScore);
        return Math.min(1.0, base + keywordBoost(queryText, candidateText));
    }

    private double keywordBoost(String queryText, String candidateText) {
        String q = queryText.toLowerCase(Locale.ROOT);
        String c = candidateText.toLowerCase(Locale.ROOT);
        double boost = 0.0;
        for (String token : BOOST_TOKENS) {
            if (q.contains(token) && c.contains(token)) {
                boost += 0.05;
            }
        }
        return Math.min(boost, 0.2);
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
