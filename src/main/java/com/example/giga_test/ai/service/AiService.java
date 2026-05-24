package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.ai.config.AiSearchProperties;
import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.model.Status;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {
    private final LlmJsonGateway llmJsonGateway;
    private final TaskRepository taskRepository;
    private final KnowledgeBaseArticleRepository articleRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final AiSearchProperties searchProperties;

    public AiService(LlmJsonGateway llmJsonGateway, TaskRepository taskRepository, KnowledgeBaseArticleRepository articleRepository, EmbeddingService embeddingService, JdbcTemplate jdbcTemplate, AiSearchProperties searchProperties) {
        this.llmJsonGateway = llmJsonGateway;
        this.taskRepository = taskRepository;
        this.articleRepository = articleRepository;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.searchProperties = searchProperties;
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
        var taskItems = hybridTicketSearch(text);

        var resolvedTasks = taskRepository.findAllByStatus(Status.RESOLVED);
        resolvedTasks.forEach(embeddingService::upsertTaskEmbedding);

        String category = classify(text).category();
        Set<String> queryHints = extractHintTokens(text, category);
        var resolvedCases = embeddingService.topK("TASK", text, 20).stream()
                .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), text, resolvedTasks))
                .filter(Objects::nonNull)
                .filter(rc -> containsHintToken(queryHints, rc.title() + " " + rc.resolutionComment()))
                .filter(rc -> rc.fitPercent() >= searchProperties.getMinRelevanceScore() * 100.0)
                .sorted(Comparator.comparingDouble(ResolvedCaseItem::fitPercent).reversed())
                .limit(searchProperties.getRagLimit())
                .toList();

        var kbArticles = articleRepository.findAll();
        kbArticles.forEach(a -> embeddingService.upsertKnowledgeEmbedding(a.getId(), a.getTitle() + " " + a.getContent()));
        var articleById = kbArticles.stream().collect(Collectors.toMap(a -> a.getId(), a -> a));
        var articles = embeddingService.topK("KB", text, 20).stream()
                .map(scored -> {
                    var article = articleById.get(scored.record().getSourceId());
                    if (article == null) return null;
                    if (!containsHintToken(queryHints, article.getTitle() + " " + article.getContent())) return null;
                    double boosted = hybridScore(scored.score(), score(text, article.getTitle() + " " + article.getContent()), text, article.getTitle() + " " + article.getContent());
                    if (boosted < searchProperties.getMinRelevanceScore()) return null;
                    return article.getTitle() + " (релевантность: " + roundToTwoDecimals(boosted * 100.0) + "%)";
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(searchProperties.getRagLimit())
                .toList();

        return new SimilarResponse(taskItems, resolvedCases, articles,
                new Explainability("RAG_RETRIEVAL", List.of("resolved_tickets", "knowledge_base", "vector_records"), "N/A", null));
    }

    private List<SimilarItem> hybridTicketSearch(String query) {
        var fts = jdbcTemplate.query(
                """
                SELECT id, title, ts_rank_cd(
                    to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,'')),
                    plainto_tsquery('simple', ?)
                ) AS rank
                FROM tasks
                WHERE to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,'')) @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """,
                (rs, rn) -> new SimilarItem(rs.getLong("id"), rs.getString("title"), rs.getDouble("rank")),
                query, query, searchProperties.getFtsLimit()
        );

        var vector = embeddingService.topK("TASK", query, searchProperties.getVectorLimit()).stream()
                .map(v -> new SimilarItem(v.record().getSourceId(), "", Math.max(0.0, v.score())))
                .toList();

        Map<Long, Double> merged = new HashMap<>();
        for (int i = 0; i < fts.size(); i++) {
            merged.merge(fts.get(i).ticketId(), 1.0 / (60 + i + 1), Double::sum);
        }
        for (int i = 0; i < vector.size(); i++) {
            merged.merge(vector.get(i).ticketId(), 1.0 / (60 + i + 1), Double::sum);
        }

        Map<Long, TaskEntity> taskMap = taskRepository.findAllById(merged.keySet()).stream()
                .collect(Collectors.toMap(TaskEntity::getId, t -> t));

        String category = classify(query).category();
        Set<String> queryHints = extractHintTokens(query, category);
        var pre = merged.entrySet().stream()
                .map(e -> {
                    TaskEntity t = taskMap.get(e.getKey());
                    if (t == null) return null;
                    String text = t.getTitle() + " " + (t.getDescription() == null ? "" : t.getDescription());
                    double boosted = e.getValue() + keywordBoost(query, text);
                    if (queryHints.contains("vpn") && !containsHintToken(queryHints, text)) {
                        boosted *= 0.2;
                    }
                    return new SimilarItem(t.getId(), t.getTitle(), boosted);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(SimilarItem::score).reversed())
                .limit(searchProperties.getRerankLimit())
                .toList();

        var candidates = pre.stream()
                .map(i -> {
                    TaskEntity t = taskMap.get(i.ticketId());
                    String summary = t == null ? "" : (t.getDescription() == null ? "" : t.getDescription());
                    return new LlmJsonGateway.CandidateForRerank(i.ticketId(), i.title(), summary);
                })
                .toList();
        var reranked = llmJsonGateway.rerankTickets(query, candidates);
        if (reranked.isEmpty()) {
            return pre.stream().limit(searchProperties.getFinalLimit()).toList();
        }
        return pre.stream()
                .map(i -> new SimilarItem(i.ticketId(), i.title(), reranked.getOrDefault(i.ticketId(), 0.0)))
                .sorted(Comparator.comparingDouble(SimilarItem::score).reversed())
                .limit(searchProperties.getFinalLimit())
                .toList();
    }

    private Set<String> extractHintTokens(String text, String category) {
        String lc = text.toLowerCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        List<String> domain = searchProperties.getDomainTokens().getOrDefault(category, List.of());
        Set<String> tokens = new HashSet<>(searchProperties.allTokens());
        tokens.addAll(domain);
        for (String token : tokens) {
            if (lc.contains(token)) out.add(token);
        }
        return out;
    }

    private boolean containsHintToken(Set<String> hints, String candidateText) {
        if (hints == null || hints.isEmpty()) return true;
        String c = candidateText == null ? "" : candidateText.toLowerCase(Locale.ROOT);
        for (String h : hints) {
            if (c.contains(h)) return true;
        }
        return false;
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
        double base = (searchProperties.getVectorWeight() * normalizedVector) + (searchProperties.getLexicalWeight() * lexicalScore);
        return Math.min(1.0, base + keywordBoost(queryText, candidateText));
    }

    private double keywordBoost(String queryText, String candidateText) {
        String q = queryText.toLowerCase(Locale.ROOT);
        String c = candidateText.toLowerCase(Locale.ROOT);
        double boost = 0.0;
        for (String token : searchProperties.allTokens()) {
            if (q.contains(token) && c.contains(token)) {
                boost += searchProperties.getTokenBoostStep();
            }
        }
        return Math.min(boost, searchProperties.getTokenBoostCap());
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
