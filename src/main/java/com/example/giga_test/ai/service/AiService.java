package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.ai.config.AiSearchProperties;
import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.ai.dto.AiQualityReportDto;
import com.example.giga_test.model.Status;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ticket.repository.AiRecommendationRepository;
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
    private final AiRecommendationRepository aiRecommendationRepository;
    private final AuditLogRepository auditLogRepository;

    public AiService(LlmJsonGateway llmJsonGateway, TaskRepository taskRepository, KnowledgeBaseArticleRepository articleRepository, EmbeddingService embeddingService, JdbcTemplate jdbcTemplate, AiSearchProperties searchProperties, AiRecommendationRepository aiRecommendationRepository, AuditLogRepository auditLogRepository) {
        this.llmJsonGateway = llmJsonGateway;
        this.taskRepository = taskRepository;
        this.articleRepository = articleRepository;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.searchProperties = searchProperties;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.auditLogRepository = auditLogRepository;
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
                new Explainability(useLlm ? "LLM_JSON" : "FALLBACK_RULES", List.of("ticket_text"), llm.status(), llm.raw(),
                        useLlm ? null : fallbackReason(llm.status())));
    }

    public SimilarResponse similar(String text) {
        var taskItems = hybridTicketSearch(text);

        var resolvedTasks = taskRepository.findAllByStatus(Status.RESOLVED);

        String category = classify(text).category();
        Set<String> queryHints = extractHintTokens(text, category);
        var taskVectors = embeddingService.topK("TASK", text, searchProperties.getVectorLimit()).stream()
                .filter(scored -> scored.score() >= searchProperties.getMinRelevanceScore())
                .toList();
        var resolvedCases = taskVectors.stream()
                .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), text, resolvedTasks))
                .filter(Objects::nonNull)
                .filter(rc -> containsHintToken(queryHints, rc.title() + " " + rc.resolutionComment()))
                .sorted(Comparator.comparingDouble(ResolvedCaseItem::fitPercent).reversed())
                .limit(searchProperties.getRagLimit())
                .toList();
        if (resolvedCases.isEmpty()) {
            resolvedCases = taskVectors.stream()
                    .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), text, resolvedTasks))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(ResolvedCaseItem::fitPercent).reversed())
                    .limit(searchProperties.getRagLimit())
                    .toList();
        }

        var kbArticles = articleRepository.findAll();
        var articleById = kbArticles.stream().collect(Collectors.toMap(a -> a.getId(), a -> a));
        var articleVectors = embeddingService.topK("KB", text, searchProperties.getVectorLimit()).stream()
                .filter(scored -> scored.score() >= searchProperties.getMinRelevanceScore())
                .toList();
        var articles = articleVectors.stream()
                .map(scored -> mapArticleHit(scored.score(), text, queryHints, articleById.get(scored.record().getSourceId()), true))
                .filter(Objects::nonNull)
                .distinct()
                .limit(searchProperties.getRagLimit())
                .toList();
        if (articles.isEmpty()) {
            articles = articleVectors.stream()
                    .map(scored -> mapArticleHit(scored.score(), text, queryHints, articleById.get(scored.record().getSourceId()), false))
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(searchProperties.getRagLimit())
                    .toList();
        }

        return new SimilarResponse(taskItems, resolvedCases, articles,
                new Explainability("RAG_RETRIEVAL", List.of("resolved_tickets", "knowledge_base", "vector_records"), "N/A", null, null));
    }

    private List<SimilarItem> hybridTicketSearch(String query) {
        var fts = jdbcTemplate.query(
                //fts поиск похожего
                """ 
                SELECT id, title, ts_rank_cd(
                    to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,'')),
                    plainto_tsquery('simple', ?)
                ) AS rank
                FROM tasks
                WHERE to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,''))
                 @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """,
                (rs, rn) -> new SimilarItem(rs.getLong("id"),
                        rs.getString("title"), rs.getDouble("rank")),
                query, query, searchProperties.getFtsLimit()
        );

        var vector = embeddingService.topK("TASK", query, searchProperties.getVectorLimit()).stream()
                .filter(v -> v.score() >= searchProperties.getMinRelevanceScore())
                .map(v -> new SimilarItem(v.record().getSourceId(), "", Math.max(0.0, v.score())))
                .toList();

        final Set<String> retrievalHints = extractHintTokens(query, classify(query).category());
        var tokenMatches = findByHintTokens(retrievalHints);

        Map<Long, Double> merged = new HashMap<>();
        for (SimilarItem item : vector) {
            merged.merge(item.ticketId(), item.score() * searchProperties.getVectorWeight(), Double::sum);
        }
        for (SimilarItem item : fts) {
            merged.merge(item.ticketId(), Math.min(1.0, item.score()) * searchProperties.getLexicalWeight(), Double::sum);
        }
        for (SimilarItem item : tokenMatches) {
            merged.merge(item.ticketId(), searchProperties.getTokenBoostStep(), Double::sum);
        }

        Map<Long, TaskEntity> taskMap = taskRepository.findAllById(merged.keySet()).stream()
                .collect(Collectors.toMap(TaskEntity::getId, t -> t));

        String category = classify(query).category();
        final Set<String> queryHints = extractHintTokens(query, category);
        var pre = merged.entrySet().stream()
                .map(e -> {
                    TaskEntity t = taskMap.get(e.getKey());
                    if (t == null) return null;
                    String text = t.getTitle() + " " + (t.getDescription() == null ? "" : t.getDescription());
                    double lexical = score(query, text);
                    double boosted = e.getValue() + lexical * searchProperties.getLexicalWeight() + keywordBoost(query, text);
                    if (boosted < searchProperties.getMinRelevanceScore()) return null;
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
                .map(i -> {
                    double llmScore = Math.max(0.0, reranked.getOrDefault(i.ticketId(), 0.0));
                    double combined = llmScore == 0.0 ? i.score() : (0.7 * llmScore) + (0.3 * i.score());
                    return new SimilarItem(i.ticketId(), i.title(), combined);
                })
                .filter(i -> i.score() >= searchProperties.getMinRelevanceScore())
                .sorted(Comparator.comparingDouble(SimilarItem::score).reversed())
                .limit(searchProperties.getFinalLimit())
                .toList();
    }

    private List<SimilarItem> findByHintTokens(Set<String> hints) {
        if (hints == null || hints.isEmpty()) return List.of();
        String[] patterns = hints.stream().map(h -> "%" + h + "%").toArray(String[]::new);
        return taskRepository.findAll().stream()
                .filter(t -> {
                    String text = (t.getTitle() + " " + (t.getDescription() == null ? "" : t.getDescription())).toLowerCase(Locale.ROOT);
                    for (String p : patterns) {
                        String token = p.substring(1, p.length() - 1);
                        if (text.contains(token)) return true;
                    }
                    return false;
                })
                .map(t -> new SimilarItem(t.getId(), t.getTitle(), 1.0))
                .limit(30)
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

    private String mapArticleHit(double vectorScore, String queryText, Set<String> queryHints, com.example.giga_test.ai.entity.KnowledgeBaseArticle article, boolean requireHints) {
        if (article == null) return null;
        String articleText = article.getTitle() + " " + article.getContent();
        if (requireHints && !containsHintToken(queryHints, articleText)) return null;
        double boosted = hybridScore(vectorScore, score(queryText, articleText), queryText, articleText);
        if (boosted < searchProperties.getMinRelevanceScore()) return null;
        return article.getTitle() + " (релевантность: " + roundToTwoDecimals(boosted * 100.0) + "%)";
    }

    public RecommendResponse recommend(String text) {
        var sim = similar(text);
        String ragPrompt = buildRagPrompt(text, sim);
        String recommendation = llmJsonGateway.recommend(ragPrompt);
        String degradedReason = degradedRecommendationReason(recommendation);
        return new RecommendResponse(recommendation,
                List.of("Проверить похожие кейсы: "+sim.resolvedCases().size(), "Определить маршрутизацию на 2-ю линию", "Подтвердить SLA и эскалацию"),
                new Explainability(degradedReason == null ? "RAG_PLUS_LLM" : "RAG_PLUS_FALLBACK",
                        List.of("resolved_tickets", "knowledge_base", "vector_records"),
                        degradedReason == null ? "OK" : "DEGRADED", recommendation, degradedReason));
    }

    private String fallbackReason(String status) {
        return switch (status == null ? "" : status) {
            case "RATE_LIMIT" -> "Сработал fallback: внешний LLM-сервис вернул ограничение частоты запросов или исчерпание квоты.";
            case "NETWORK_UNAVAILABLE" -> "Сработал fallback: внешний LLM-сервис недоступен из-за сетевой ошибки или ошибки DNS.";
            case "MISCONFIGURED" -> "Сработал fallback: не настроен ключ доступа к внешнему LLM-сервису.";
            case "LLM_UNAVAILABLE" -> "Сработал fallback: внешний LLM-сервис временно недоступен.";
            case "INVALID_JSON_CONTRACT" -> "Сработал fallback: ответ LLM не соответствует ожидаемому JSON-контракту.";
            default -> "Сработал fallback: LLM-ответ не может быть использован для автоматической обработки.";
        };
    }

    private String degradedRecommendationReason(String recommendation) {
        String normalized = recommendation == null ? "" : recommendation.toLowerCase(Locale.ROOT);
        if (normalized.contains("429") || normalized.contains("квота") || normalized.contains("too many")) {
            return fallbackReason("RATE_LIMIT");
        }
        if (normalized.contains("сети") || normalized.contains("dns") || normalized.contains("network")) {
            return fallbackReason("NETWORK_UNAVAILABLE");
        }
        if (normalized.contains("misconfigured") || normalized.contains("auth key")) {
            return fallbackReason("MISCONFIGURED");
        }
        if (normalized.contains("temporary unavailable") || normalized.contains("manual triage") || normalized.contains("недоступ")) {
            return fallbackReason("LLM_UNAVAILABLE");
        }
        return null;
    }


    public AiQualityReportDto qualityReport() {
        var recommendations = aiRecommendationRepository.findAll();
        long total = recommendations.size();
        long evaluated = recommendations.stream().filter(r -> r.getAccepted() != null).count();
        long accepted = recommendations.stream().filter(r -> Boolean.TRUE.equals(r.getAccepted())).count();
        double acceptanceRate = evaluated == 0 ? 0 : accepted * 100.0 / evaluated;
        double avgScore = recommendations.stream()
                .filter(r -> r.getUsefulnessScore() != null)
                .mapToInt(r -> r.getUsefulnessScore())
                .average()
                .orElse(0);
        long classificationChanges = auditLogRepository.countByAction("CLASSIFICATION_UPDATE");
        long ticketsTotal = taskRepository.count();
        double classificationChangeRate = ticketsTotal == 0 ? 0 : classificationChanges * 100.0 / ticketsTotal;
        return new AiQualityReportDto(total, evaluated, accepted, acceptanceRate, avgScore, classificationChanges, classificationChangeRate);
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
