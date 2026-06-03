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
        String lc = (text == null ? "" : text).toLowerCase(Locale.ROOT);
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
                .map(v -> new SimilarItem(v.record().getSourceId(), "", clampScore(v.score())))
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
                    double boosted = clampScore(e.getValue() + lexical * searchProperties.getLexicalWeight() + keywordBoost(query, text));
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
                    double llmScore = clampScore(reranked.getOrDefault(i.ticketId(), 0.0));
                    double combined = clampScore(llmScore == 0.0 ? i.score() : (0.7 * llmScore) + (0.3 * i.score()));
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
        Set<String> queryTokens = normalizedTokens(embeddingService.expandTextForSearch(text));
        Set<String> out = new HashSet<>();
        List<String> domain = searchProperties.getDomainTokens().getOrDefault(category, List.of());
        Set<String> tokens = new HashSet<>(searchProperties.allTokens());
        tokens.addAll(domain);
        for (String token : tokens) {
            Set<String> tokenForms = normalizedTokens(token);
            if (!tokenForms.isEmpty() && tokenForms.stream().anyMatch(queryTokens::contains)) {
                out.add(token);
            }
        }
        return out;
    }

    private boolean containsHintToken(Set<String> hints, String candidateText) {
        if (hints == null || hints.isEmpty()) return true;
        Set<String> candidateTokens = normalizedTokens(embeddingService.expandTextForSearch(candidateText));
        for (String h : hints) {
            Set<String> hintTokens = normalizedTokens(h);
            if (!hintTokens.isEmpty() && hintTokens.stream().anyMatch(candidateTokens::contains)) return true;
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
        return recommend(text, null, List.of());
    }

    public RecommendResponse recommend(String text, String mode, List<String> sourceHints) {
        var sim = similar(text);
        String normalizedMode = normalizeRecommendationMode(mode);
        String ragPrompt = buildRagPrompt(text, sim, normalizedMode, sourceHints == null ? List.of() : sourceHints);
        String llmRecommendation = llmJsonGateway.recommend(ragPrompt);
        String degradedReason = degradedRecommendationReason(llmRecommendation);
        GroundedRecommendation grounded = buildGroundedRecommendation(text, sim, llmRecommendation, normalizedMode);
        return new RecommendResponse(grounded.recommendation(), grounded.steps(),
                new Explainability(grounded.usedRagSources() ? "RAG_GROUNDED_" + normalizedMode : (degradedReason == null ? "RAG_PLUS_LLM_" + normalizedMode : "RAG_PLUS_FALLBACK_" + normalizedMode),
                        List.of("knowledge_base", "resolved_tickets", "vector_records"),
                        degradedReason == null ? "OK" : "DEGRADED", llmRecommendation, degradedReason));
    }

    public RecommendResponse rewriteRecommendation(String text, String action, String context) {
        String normalizedAction = normalizeRewriteAction(action);
        String prompt = buildRewritePrompt(text, normalizedAction, context);
        String rewritten = llmJsonGateway.recommend(prompt);
        String degradedReason = degradedRecommendationReason(rewritten);
        String finalText = degradedReason == null ? sanitizeLlmRecommendation(rewritten) : text;
        List<String> steps = extractSentences(finalText).stream()
                .map(this::cleanStep)
                .filter(step -> !step.isBlank())
                .limit(3)
                .toList();
        return new RecommendResponse(finalText, steps,
                new Explainability("DRAFT_REWRITE_" + normalizedAction,
                        List.of("operator_draft", "ticket_context"),
                        degradedReason == null ? "OK" : "DEGRADED", rewritten, degradedReason));
    }

    private GroundedRecommendation buildGroundedRecommendation(String text, SimilarResponse sim, String llmRecommendation, String mode) {
        boolean hasArticles = sim.articles() != null && !sim.articles().isEmpty();
        boolean hasCases = sim.resolvedCases() != null && !sim.resolvedCases().isEmpty();
        if (!hasArticles && !hasCases) {
            String fallback = llmRecommendation == null || llmRecommendation.isBlank()
                    ? "По обращению не найдены релевантные статьи базы знаний и решённые инциденты. Передайте заявку оператору для ручного анализа."
                    : llmRecommendation;
            return new GroundedRecommendation(formatModeFallbackRecommendation(fallback, mode), List.of(
                    "Проверить обращение вручную",
                    "Уточнить недостающие данные у автора",
                    "Назначить ответственную группу по классификации обращения"
            ), false);
        }

        List<String> steps = extractGroundedSteps(sim);
        List<String> articleLines = sim.articles() == null ? List.of() : sim.articles().stream()
                .limit(3)
                .map(article -> article.title() + " — " + article.fitPercent() + "%")
                .toList();
        List<String> caseLines = sim.resolvedCases() == null ? List.of() : sim.resolvedCases().stream()
                .limit(3)
                .map(c -> "#" + c.ticketId() + " " + c.title() + " — " + c.fitPercent() + "%")
                .toList();
        String conciseLlm = sanitizeLlmRecommendation(llmRecommendation);
        StringBuilder sb = new StringBuilder();
        appendModeHeader(sb, mode);
        sb.append("## Что известно из источников\n");
        if (!articleLines.isEmpty()) {
            sb.append("**Статьи базы знаний:**\n");
            articleLines.forEach(line -> sb.append("- ").append(line).append("\n"));
        }
        if (!caseLines.isEmpty()) {
            sb.append("**Похожие решённые обращения:**\n");
            caseLines.forEach(line -> sb.append("- ").append(line).append("\n"));
        }
        sb.append("\n");
        appendModeBody(sb, mode, steps, conciseLlm, inferRouting(sim));
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
        Set<String> query = normalizedTokens(embeddingService.expandTextForSearch(a));
        Set<String> candidate = normalizedTokens(embeddingService.expandTextForSearch(b));
        if (query.isEmpty() || candidate.isEmpty()) return 0;
        double matched = 0;
        for (String token : query) {
            if (candidate.contains(token) || candidate.stream().anyMatch(c -> tokenSimilarity(token, c) >= 0.82)) {
                matched += 1.0;
            }
        }
        return clampScore(matched / query.size());
    }

    private Set<String> normalizedTokens(String text) {
        String normalized = (text == null ? "" : text)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim();
        if (normalized.isBlank()) return Set.of();
        return Arrays.stream(normalized.split("\\s+"))
                .map(this::stemToken)
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String stemToken(String token) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (t.length() <= 4) return t;
        String[] endings = {"иями", "ями", "ами", "ого", "ему", "ыми", "ими", "иях", "ах", "ях", "ов", "ев", "ей", "ом", "ем", "ою", "ею", "ую", "юю", "ая", "яя", "ое", "ее", "ые", "ие", "ый", "ий", "ой", "ам", "ям", "а", "я", "ы", "и", "у", "ю", "е", "о", "ь", "ъ"};
        for (String ending : endings) {
            if (t.endsWith(ending) && t.length() - ending.length() >= 3) {
                return t.substring(0, t.length() - ending.length());
            }
        }
        return t;
    }

    private double tokenSimilarity(String left, String right) {
        if (left.length() < 4 || right.length() < 4) return 0.0;
        Set<String> a = characterNgrams(left, 3);
        Set<String> b = characterNgrams(right, 3);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        long intersection = a.stream().filter(b::contains).count();
        long union = java.util.stream.Stream.concat(a.stream(), b.stream()).distinct().count();
        return union == 0 ? 0.0 : intersection * 1.0 / union;
    }

    private Set<String> characterNgrams(String token, int size) {
        if (token.length() < size) return Set.of(token);
        Set<String> out = new HashSet<>();
        for (int i = 0; i + size <= token.length(); i++) {
            out.add(token.substring(i, i + size));
        }
        return out;
    }

    private double clampScore(double score) {
        if (Double.isNaN(score) || Double.isInfinite(score)) return 0.0;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private double roundToTwoDecimals(double value) { return Math.round(Math.max(0.0, Math.min(100.0, value)) * 100.0) / 100.0; }

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
        Set<String> queryTokens = normalizedTokens(embeddingService.expandTextForSearch(queryText));
        Set<String> candidateTokens = normalizedTokens(embeddingService.expandTextForSearch(candidateText));
        double boost = 0.0;
        for (String token : searchProperties.allTokens()) {
            Set<String> tokenForms = normalizedTokens(token);
            if (!tokenForms.isEmpty() && tokenForms.stream().anyMatch(queryTokens::contains) && tokenForms.stream().anyMatch(candidateTokens::contains)) {
                boost += searchProperties.getTokenBoostStep();
            }
        }
        return Math.min(boost, searchProperties.getTokenBoostCap());
    }

    private String normalizeRewriteAction(String action) {
        if (action == null || action.isBlank()) return "POLITE";
        String value = action.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SHORTEN", "POLITE", "TECHNICAL_DETAIL" -> value;
            default -> "POLITE";
        };
    }

    private String buildRewritePrompt(String text, String action, String context) {
        String instruction = switch (normalizeRewriteAction(action)) {
            case "SHORTEN" -> "Сократи черновик: оставь только суть, убери повторы, сохрани факты и важные шаги.";
            case "TECHNICAL_DETAIL" -> "Сделай черновик технически подробнее: добавь проверяемые действия, условия и ожидаемый результат, не выдумывай факты.";
            default -> "Сделай черновик более вежливым и понятным, сохрани смысл и факты, не добавляй лишних обещаний.";
        };
        return """
                Ты редактор ответа службы поддержки.
                Верни только итоговый текст без JSON, без markdown-забора и без пояснений о редактировании.
                %s

                Контекст обращения:
                %s

                Черновик:
                %s
                """.formatted(instruction, context == null ? "—" : context, text);
    }

    private String buildRagPrompt(String text, SimilarResponse sim, String mode, List<String> sourceHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты помощник техподдержки. Сформируй ответ в режиме: ").append(recommendationModeTitle(mode)).append(".\n");
        sb.append("Пиши по-русски, коротко, без markdown-звёздочек вокруг заголовков, без повторов и без общих фраз. ")
                .append("Опирайся сначала на источники, затем на общий опыт поддержки.\n\n");
        sb.append("Обращение:\n").append(text).append("\n\n");
        if (sourceHints != null && !sourceHints.isEmpty()) {
            sb.append("Выбранные оператором источники с повышенным приоритетом:\n");
            sourceHints.forEach(hint -> sb.append("- ").append(hint).append("\n"));
            sb.append("\n");
        }
        sb.append("Похожие решенные кейсы:\n");
        sim.resolvedCases().forEach(c -> sb.append("- ").append(c.title()).append(" [").append(c.fitPercent()).append("%]: ").append(c.resolutionComment()).append("\n"));
        sb.append("\nРелевантные статьи БЗ:\n");
        sim.articles().forEach(a -> sb.append("- ").append(a.title()).append(" [").append(a.fitPercent()).append("%, ").append(a.category()).append("]: ").append(a.content()).append("\n"));
        sb.append("\nВерни только полезный текст для выбранного режима. Если источников нет, явно напиши, что требуется ручная обработка оператором.");
        return sb.toString();
    }

    private String normalizeRecommendationMode(String mode) {
        if (mode == null || mode.isBlank()) return "STEP_BY_STEP";
        String value = mode.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SHORT", "STEP_BY_STEP", "USER_REPLY", "INTERNAL_COMMENT", "TECHNICAL_GUIDE", "ESCALATION_SUMMARY" -> value;
            default -> "STEP_BY_STEP";
        };
    }

    private String recommendationModeTitle(String mode) {
        return switch (normalizeRecommendationMode(mode)) {
            case "SHORT" -> "краткая рекомендация";
            case "USER_REPLY" -> "ответ пользователю";
            case "INTERNAL_COMMENT" -> "внутренний комментарий оператору";
            case "TECHNICAL_GUIDE" -> "техническая инструкция";
            case "ESCALATION_SUMMARY" -> "резюме для эскалации";
            default -> "пошаговое решение";
        };
    }

    private void appendModeHeader(StringBuilder sb, String mode) {
        sb.append("# ").append(recommendationModeTitle(mode)).append("\n\n");
    }

    private void appendModeBody(StringBuilder sb, String mode, List<String> steps, String llmRecommendation, String routing) {
        String normalized = normalizeRecommendationMode(mode);
        if ("SHORT".equals(normalized)) {
            sb.append("## Рекомендация\n").append(llmRecommendation).append("\n\n");
            sb.append("## Первые действия\n");
            steps.stream().limit(2).forEach(step -> sb.append("- ").append(step).append("\n"));
            return;
        }
        if ("USER_REPLY".equals(normalized)) {
            sb.append("## Готовый ответ пользователю\n").append(llmRecommendation).append("\n\n");
            sb.append("## Что проверить оператору перед отправкой\n");
            steps.stream().limit(3).forEach(step -> sb.append("- ").append(step).append("\n"));
            return;
        }
        if ("INTERNAL_COMMENT".equals(normalized)) {
            sb.append("## Внутренний комментарий\n").append(llmRecommendation).append("\n\n");
            sb.append("## Следующие действия оператора\n");
            steps.stream().limit(3).forEach(step -> sb.append("- ").append(step).append("\n"));
            return;
        }
        if ("TECHNICAL_GUIDE".equals(normalized)) {
            sb.append("## Техническая инструкция\n");
            for (int i = 0; i < steps.size(); i++) {
                sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
            sb.append("\n## Маршрутизация\n").append(routing).append("\n");
            return;
        }
        if ("ESCALATION_SUMMARY".equals(normalized)) {
            sb.append("## Резюме для эскалации\n").append(llmRecommendation).append("\n\n");
            sb.append("## Что уже можно проверить\n");
            steps.stream().limit(3).forEach(step -> sb.append("- ").append(step).append("\n"));
            sb.append("\n## Предлагаемая маршрутизация\n").append(routing).append("\n");
            return;
        }
        sb.append("## Пошаговое решение\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        sb.append("\n## Комментарий ИИ\n").append(llmRecommendation).append("\n");
        sb.append("\n## Маршрутизация\n").append(routing).append("\n");
    }

    private String sanitizeLlmRecommendation(String raw) {
        if (raw == null || raw.isBlank() || degradedRecommendationReason(raw) != null) {
            return "Используйте найденные источники как основу решения и проверьте детали обращения перед ответом пользователю.";
        }
        String cleaned = raw.replace("\r", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("(?m)^\\s*[-*]\\s+", "- ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        if (cleaned.length() > 1200) {
            return cleaned.substring(0, 1200).trim() + "…";
        }
        return cleaned;
    }

    private String formatModeFallbackRecommendation(String fallback, String mode) {
        return "# " + recommendationModeTitle(mode) + "\n\n## Рекомендация\n" + fallback;
    }

}
