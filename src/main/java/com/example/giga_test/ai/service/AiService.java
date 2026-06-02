package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.ai.config.AiSearchProperties;
import com.example.giga_test.ai.integration.LlmJsonGateway;
import com.example.giga_test.ai.entity.KnowledgeBaseArticle;
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
        String category = inferCategoryForSearch(text);
        Set<String> queryHints = extractHintTokens(text, category);
        var taskVectors = embeddingService.topK("TASK", text, searchProperties.getVectorLimit()).stream()
                .filter(scored -> scored.score() >= searchProperties.getMinRelevanceScore())
                .toList();
        var taskById = taskRepository.findAllById(sourceIds(taskVectors)).stream()
                .collect(Collectors.toMap(TaskEntity::getId, t -> t));

        var taskItems = hybridTicketSearch(text, category, taskVectors);

        var resolvedCases = taskVectors.stream()
                .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), text, taskById))
                .filter(Objects::nonNull)
                .filter(rc -> containsHintToken(queryHints, rc.title() + " " + rc.resolutionComment()))
                .sorted(Comparator.comparingDouble(ResolvedCaseItem::fitPercent).reversed())
                .limit(searchProperties.getRagLimit())
                .toList();
        if (resolvedCases.isEmpty()) {
            resolvedCases = taskVectors.stream()
                    .map(scored -> mapResolvedCase(scored.record().getSourceId(), scored.score(), text, taskById))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(ResolvedCaseItem::fitPercent).reversed())
                    .limit(searchProperties.getRagLimit())
                    .toList();
        }

        var articleVectors = embeddingService.topK("KB", text, searchProperties.getVectorLimit()).stream()
                .filter(scored -> scored.score() >= searchProperties.getMinRelevanceScore())
                .toList();
        var articleById = articleRepository.findAllById(sourceIds(articleVectors)).stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a));
        var articles = articleVectors.stream()
                .map(scored -> mapArticleHit(scored.score(), text, queryHints, articleById.get(scored.record().getSourceId()), true))
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(KnowledgeArticleItem::articleId, item -> item, AiService::bestArticleHit, LinkedHashMap::new),
                        map -> map.values().stream()
                                .sorted(Comparator.comparingDouble(KnowledgeArticleItem::fitPercent).reversed())
                                .limit(searchProperties.getRagLimit())
                                .toList()
                ));
        if (articles.isEmpty()) {
            articles = articleVectors.stream()
                    .map(scored -> mapArticleHit(scored.score(), text, queryHints, articleById.get(scored.record().getSourceId()), false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(KnowledgeArticleItem::articleId, item -> item, AiService::bestArticleHit, LinkedHashMap::new),
                            map -> map.values().stream()
                                    .sorted(Comparator.comparingDouble(KnowledgeArticleItem::fitPercent).reversed())
                                    .limit(searchProperties.getRagLimit())
                                    .toList()
                    ));
        }
        articles = mergeArticleHits(articles, lexicalArticleSearch(text));

        return new SimilarResponse(taskItems, resolvedCases, articles,
                new Explainability("RAG_RETRIEVAL", List.of("resolved_tickets", "knowledge_base", "vector_records"), "N/A", null, null));
    }

    private List<SimilarItem> hybridTicketSearch(String query, String category, List<EmbeddingService.ScoredVectorRecord> taskVectors) {
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

        var vector = taskVectors.stream()
                .map(v -> new SimilarItem(v.record().getSourceId(), "", Math.max(0.0, v.score())))
                .toList();

        final Set<String> retrievalHints = extractHintTokens(query, category);
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
        var tokens = hints.stream().filter(h -> h != null && !h.isBlank()).limit(5).toList();
        if (tokens.isEmpty()) return List.of();

        String where = tokens.stream()
                .map(ignored -> "lower(coalesce(title,'') || ' ' || coalesce(description,'')) LIKE ?")
                .collect(Collectors.joining(" OR "));
        List<Object> params = new ArrayList<>();
        tokens.forEach(token -> params.add("%" + token.toLowerCase(Locale.ROOT) + "%"));
        params.add(30);

        return jdbcTemplate.query(
                "SELECT id, title FROM tasks WHERE " + where + " LIMIT ?",
                (rs, rn) -> new SimilarItem(rs.getLong("id"), rs.getString("title"), 1.0),
                params.toArray()
        );
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

    private Set<Long> sourceIds(List<EmbeddingService.ScoredVectorRecord> vectors) {
        return vectors.stream()
                .map(v -> v.record().getSourceId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    //generation-фаза
    private KnowledgeArticleItem mapArticleHit(double vectorScore, String queryText, Set<String> queryHints, KnowledgeBaseArticle article, boolean requireHints) {
        if (article == null) return null;
        String articleText = article.getTitle() + " " + article.getContent();
        if (requireHints && !containsHintToken(queryHints, articleText)) return null;
        double boosted = hybridScore(vectorScore, score(queryText, articleText), queryText, articleText);
        if (boosted < searchProperties.getMinRelevanceScore()) return null;
        return articleItem(article, boosted);
    }

    private List<KnowledgeArticleItem> mergeArticleHits(List<KnowledgeArticleItem> primary, List<KnowledgeArticleItem> secondary) {
        return java.util.stream.Stream.concat(
                        primary == null ? java.util.stream.Stream.empty() : primary.stream(),
                        secondary == null ? java.util.stream.Stream.empty() : secondary.stream()
                )
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(KnowledgeArticleItem::articleId, item -> item, AiService::bestArticleHit, LinkedHashMap::new),
                        map -> map.values().stream()
                                .sorted(Comparator.comparingDouble(KnowledgeArticleItem::fitPercent).reversed())
                                .limit(searchProperties.getRagLimit())
                                .toList()
                ));
    }

    private static KnowledgeArticleItem bestArticleHit(KnowledgeArticleItem left, KnowledgeArticleItem right) {
        return left.fitPercent() >= right.fitPercent() ? left : right;
    }

    private List<KnowledgeArticleItem> lexicalArticleSearch(String queryText) {
        return articleRepository.findAll().stream()
                .map(article -> {
                    String articleText = article.getTitle() + " " + article.getContent();
                    double lexical = score(queryText, articleText);
                    double titleLexical = score(queryText, article.getTitle());
                    double relevance = Math.min(1.0, (lexical * 0.65) + (titleLexical * 0.35) + keywordBoost(queryText, articleText));
                    if (relevance < Math.max(0.08, searchProperties.getMinRelevanceScore() / 2.0)) return null;
                    return articleItem(article, relevance);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(KnowledgeArticleItem::fitPercent).reversed())
                .limit(searchProperties.getRagLimit())
                .toList();
    }

    private KnowledgeArticleItem articleItem(KnowledgeBaseArticle article, double relevance) {
        return new KnowledgeArticleItem(article.getId(), article.getTitle(), roundToTwoDecimals(relevance * 100.0),
                article.getContent(), article.getCategory());
    }

    public RecommendResponse recommend(String text) {
        var sim = similar(text);
        //сборка промпта
        String ragPrompt = buildRagPrompt(text, sim);
        String llmRecommendation = llmJsonGateway.recommend(ragPrompt);
        String degradedReason = degradedRecommendationReason(llmRecommendation);
        GroundedRecommendation grounded = buildGroundedRecommendation(text, sim, llmRecommendation);
        return new RecommendResponse(grounded.recommendation(), grounded.steps(),
                new Explainability(grounded.usedRagSources() ? "RAG_GROUNDED" : (degradedReason == null ? "RAG_PLUS_LLM" : "RAG_PLUS_FALLBACK"),
                        List.of("knowledge_base", "resolved_tickets", "vector_records"),
                        degradedReason == null ? "OK" : "DEGRADED", llmRecommendation, degradedReason));
    }

    private GroundedRecommendation buildGroundedRecommendation(String text, SimilarResponse sim, String llmRecommendation) {
        boolean hasArticles = sim.articles() != null && !sim.articles().isEmpty();
        boolean hasCases = sim.resolvedCases() != null && !sim.resolvedCases().isEmpty();
        if (!hasArticles && !hasCases) {
            String fallback = llmRecommendation == null || llmRecommendation.isBlank()
                    ? "По обращению не найдены релевантные статьи базы знаний и решённые инциденты. Передайте заявку оператору для ручного анализа."
                    : llmRecommendation;
            return new GroundedRecommendation(fallback, List.of(
                    "Проверить обращение вручную",
                    "Уточнить недостающие данные у автора",
                    "Назначить ответственную группу по классификации обращения"
            ), false);
        }

        List<String> steps = extractGroundedSteps(sim);
        StringBuilder sb = new StringBuilder();
        sb.append("Рекомендация по обращению:\n").append(text).append("\n\n");
        sb.append("Основание: ");
        if (hasArticles) {
            sb.append("статьи базы знаний");
            if (hasCases) sb.append(" и ");
        }
        if (hasCases) sb.append("похожие решённые инциденты");
        sb.append(".\n\n");

        if (hasArticles) {
            sb.append("Что говорит база знаний:\n");
            sim.articles().forEach(article -> sb.append("- ").append(article.title()).append(" [")
                    .append(article.fitPercent()).append("%]: ").append(article.content()).append("\n"));
            sb.append("\n");
        }
        if (hasCases) {
            sb.append("Что сработало в похожих решённых обращениях:\n");
            sim.resolvedCases().forEach(c -> sb.append("- #").append(c.ticketId()).append(" ").append(c.title())
                    .append(" [").append(c.fitPercent()).append("%]: ").append(c.resolutionComment()).append("\n"));
            sb.append("\n");
        }

        sb.append("Маршрутизация:\n").append(inferRouting(sim)).append("\n\n");
        sb.append("Первые действия:\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return new GroundedRecommendation(sb.toString().trim(), steps, true);
    }

    private List<String> extractGroundedSteps(SimilarResponse sim) {
        List<String> steps = new ArrayList<>();
        if (sim.articles() != null) {
            sim.articles().forEach(article -> steps.addAll(extractSentences(article.content())));
        }
        if (sim.resolvedCases() != null) {
            sim.resolvedCases().forEach(c -> steps.addAll(extractSentences(c.resolutionComment())));
        }
        return steps.stream()
                .map(this::cleanStep)
                .filter(step -> !step.isBlank())
                .distinct()
                .limit(3)
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    if (list.isEmpty()) {
                        return List.of("Изучить найденные статьи базы знаний", "Сравнить с похожими решёнными обращениями", "Назначить ответственного оператора");
                    }
                    return list;
                }));
    }

    private List<String> extractSentences(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return Arrays.stream(normalized.split("(?<=[.!?。])\\s+|;\\s*|(?<=\\.)"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String cleanStep(String step) {
        return step.replaceAll("^[\\-•\\d.)\\s]+", "").trim();
    }

    private String inferRouting(SimilarResponse sim) {
        if (sim.articles() != null && !sim.articles().isEmpty()) {
            String category = sim.articles().get(0).category();
            return "Очередь базы знаний: " + (category == null || category.isBlank() ? "GENERAL" : category);
        }
        return "Группа, решавшая наиболее похожий инцидент";
    }

    private record GroundedRecommendation(String recommendation, List<String> steps, boolean usedRagSources) {}

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
        //расчёт метрик качества
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

    private String inferCategoryForSearch(String text) {
        String lc = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lc.contains("доступ") || lc.contains("vpn") || lc.contains("впн") || lc.contains("парол") || lc.contains("mfa")) return "ACCESS";
        if (lc.contains("почт") || lc.contains("email") || lc.contains("mail") || lc.contains("outlook") || lc.contains("exchange")) return "EMAIL";
        if (lc.contains("сеть") || lc.contains("dns") || lc.contains("proxy")) return "NETWORK";
        if (lc.contains("принтер") || lc.contains("ноутбук") || lc.contains("пк")) return "WORKPLACE";
        if (lc.contains("1с") || lc.contains("sap") || lc.contains("erp")) return "ERP";
        if (lc.contains("инцид") || lc.contains("простой")) return "INCIDENT";
        return "GENERAL";
    }

    private double score(String a, String b) {
        Set<String> sa = new HashSet<>(Arrays.asList(a.toLowerCase(Locale.ROOT).split("\\s+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.toLowerCase(Locale.ROOT).split("\\s+")));
        if (sa.isEmpty()) return 0;
        long common = sa.stream().filter(sb::contains).count();
        return common * 1.0 / sa.size();
    }

    private double roundToTwoDecimals(double value) { return Math.round(value * 100.0) / 100.0; }

    private ResolvedCaseItem mapResolvedCase(Long id, double vectorScore, String queryText, Map<Long, TaskEntity> tasks) {
        TaskEntity task = tasks.get(id);
        if (task != null && task.getStatus() != Status.RESOLVED) return null;
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
        sb.append("Правило: сначала используй факты из релевантных статей базы знаний и решений похожих закрытых обращений. ")
                .append("Не заменяй их общими советами, если источники найдены. Если источники противоречат общим правилам, приоритет у источников.\n\n");
        sb.append("Похожие решенные кейсы:\n");
        sim.resolvedCases().forEach(c -> sb.append("- ").append(c.title()).append(" [").append(c.fitPercent()).append("%]: ").append(c.resolutionComment()).append("\n"));
        sb.append("\nРелевантные статьи БЗ:\n");
        sim.articles().forEach(a -> sb.append("- ").append(a.title()).append(" [").append(a.fitPercent()).append("%, ").append(a.category()).append("]: ").append(a.content()).append("\n"));
        sb.append("\nВерни: краткую рекомендацию, маршрутизацию и первые 3 действия строго на основе этих источников. ")
                .append("Если источников нет, явно напиши, что требуется ручная обработка оператором.");
        return sb.toString();
    }
}
