package com.example.giga_test.ai.service;

import com.example.giga_test.ai.entity.VectorRecord;
import com.example.giga_test.ai.repository.VectorRecordRepository;
import com.example.giga_test.integration.LlmClient;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final int DIM = 1024;
    private static final String LOCAL_HASH_PROVIDER = "LOCAL_HASH";
    private static final String SEMANTIC_EXPANSION_VERSION = "SYN_V1";
    private static final List<List<String>> SYNONYM_GROUPS = List.of(
            List.of("email", "e-mail", "mail", "электронная почта", "почта", "почтовый ящик", "письмо", "outlook", "exchange"),
            List.of("vpn", "впн", "virtual private network", "виртуальная частная сеть", "удаленный доступ", "удаленное подключение"),
            List.of("машина", "автомобиль", "авто", "транспорт", "легковой автомобиль"),
            List.of("учетная запись", "аккаунт", "пользователь", "логин", "ad", "active directory"),
            List.of("мфа", "mfa", "2fa", "двухфакторная аутентификация", "многофакторная аутентификация"),
            List.of("сертификат", "certificate", "cert", "ключ доступа"),
            List.of("принтер", "печать", "печатающее устройство", "мфу"),
            List.of("1с", "бухгалтерия", "erp", "учетная система")
    );

    private final VectorRecordRepository vectorRecordRepository;
    private final LlmClient llmClient;
    private final JdbcTemplate jdbcTemplate;

    public EmbeddingService(VectorRecordRepository vectorRecordRepository, LlmClient llmClient, JdbcTemplate jdbcTemplate) {
        this.vectorRecordRepository = vectorRecordRepository;
        this.llmClient = llmClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertTaskEmbedding(TaskEntity taskEntity) {
        String text = (taskEntity.getTitle() + " " + taskEntity.getDescription()).trim();
        if (isEmbeddingUpToDate("TASK", taskEntity.getId(), text)) {
            return;
        }
        upsert("TASK", taskEntity.getId(), text);
    }

    public void upsertKnowledgeEmbedding(Long articleId, String content) {
        if (isEmbeddingUpToDate("KB", articleId, content)) {
            return;
        }
        upsert("KB", articleId, content);
    }

    public List<ScoredVectorRecord> topK(String sourceType, String query, int k) {
        EmbeddingPayload queryEmbedding = embedPayload(query);
        String vecLiteral = toVectorLiteral(queryEmbedding.vector());
        try {
            List<ScoredVectorRecord> fromPgVector = jdbcTemplate.query(
                    """
                    SELECT id, source_type, source_id, text_content, embedding, embedding_provider, embedding_dimension,
                           1 - (embedding_vector <=> CAST(? AS vector)) AS score
                    FROM vector_records
                    WHERE source_type = ?
                      AND embedding_provider = ?
                      AND embedding_vector IS NOT NULL
                    ORDER BY embedding_vector <=> CAST(? AS vector)
                    LIMIT ?
                    """,
                    (rs, rowNum) -> mapRecord(rs, rs.getDouble("score")),
                    vecLiteral, sourceType, queryEmbedding.provider(), vecLiteral, k
            );
            if (!fromPgVector.isEmpty()) {
                return fromPgVector;
            }
        } catch (DataAccessException ignored) {
            // Fallback to the serialized embedding column when pgvector is unavailable or not initialized yet.
        }

        return vectorRecordRepository.findAllBySourceTypeAndEmbeddingProvider(sourceType, queryEmbedding.provider()).stream()
                .map(v -> new ScoredVectorRecord(v, cosine(queryEmbedding.vector(), parse(v.getEmbedding()))))
                .sorted(Comparator.comparingDouble(ScoredVectorRecord::score).reversed())
                .limit(k)
                .toList();
    }

    private void upsert(String sourceType, Long sourceId, String text) {
        EmbeddingPayload embedding = embedPayload(text);
        String serialized = serialize(embedding.vector());
        String vecLiteral = toVectorLiteral(embedding.vector());

        jdbcTemplate.update(
                """
                INSERT INTO vector_records(source_type, source_id, text_content, embedding, embedding_vector, embedding_provider, embedding_dimension)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?)
                ON CONFLICT (source_type, source_id)
                DO UPDATE SET text_content = EXCLUDED.text_content,
                              embedding = EXCLUDED.embedding,
                              embedding_vector = EXCLUDED.embedding_vector,
                              embedding_provider = EXCLUDED.embedding_provider,
                              embedding_dimension = EXCLUDED.embedding_dimension
                """,
                sourceType, sourceId, text, serialized, vecLiteral, embedding.provider(), embedding.vector().length
        );
    }

    private boolean isEmbeddingUpToDate(String sourceType, Long sourceId, String textContent) {
        String expectedProvider = expectedEmbeddingProvider();
        return vectorRecordRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .map(existing -> Objects.equals(existing.getTextContent(), textContent)
                        && Objects.equals(existing.getEmbeddingProvider(), expectedProvider)
                        && Objects.equals(existing.getEmbeddingDimension(), DIM))
                .orElse(false);
    }

    private ScoredVectorRecord mapRecord(java.sql.ResultSet rs, double score) throws java.sql.SQLException {
        VectorRecord record = VectorRecord.builder()
                .id(rs.getLong("id"))
                .sourceType(rs.getString("source_type"))
                .sourceId(rs.getLong("source_id"))
                .textContent(rs.getString("text_content"))
                .embedding(rs.getString("embedding"))
                .embeddingProvider(rs.getString("embedding_provider"))
                .embeddingDimension(rs.getInt("embedding_dimension"))
                .build();
        return new ScoredVectorRecord(record, score);
    }

    public double[] embed(String text) {
        return embedPayload(text).vector();
    }

    private EmbeddingPayload embedPayload(String text) {
        String expandedText = expandForEmbedding(text);
        double[] real = llmClient.embed(expandedText);
        if (real != null && real.length > 0) {
            return new EmbeddingPayload(fitDimAndNormalize(real, DIM), expectedEmbeddingProvider());
        }
        return new EmbeddingPayload(localHashEmbedding(expandedText), expectedLocalProvider());
    }

    private String expectedEmbeddingProvider() {
        String provider = llmClient.embeddingProviderKey();
        if (provider == null || provider.isBlank() || Objects.equals(provider, LOCAL_HASH_PROVIDER)) {
            return expectedLocalProvider();
        }
        return provider + ":" + SEMANTIC_EXPANSION_VERSION;
    }

    private String expectedLocalProvider() {
        return LOCAL_HASH_PROVIDER + ":" + SEMANTIC_EXPANSION_VERSION;
    }

    private String expandForEmbedding(String text) {
        String original = text == null ? "" : text;
        String normalized = normalizeForSynonyms(original);
        Set<String> expansions = new LinkedHashSet<>();
        for (List<String> group : SYNONYM_GROUPS) {
            boolean matched = group.stream().map(this::normalizeForSynonyms).anyMatch(normalized::contains);
            if (matched) {
                expansions.addAll(group);
            }
        }
        if (expansions.isEmpty()) {
            return original;
        }
        return original + "\nСинонимы и эквивалентные термины: " + String.join(", ", expansions);
    }

    private String normalizeForSynonyms(String text) {
        return (text == null ? "" : text)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim();
    }

    private double[] localHashEmbedding(String text) {
        double[] vec = new double[DIM];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        String[] tokens = normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            addFeature(vec, token, 1.0);
            for (int i = 0; i + 3 <= token.length(); i++) {
                addFeature(vec, token.substring(i, i + 3), 0.25);
            }
        }
        normalize(vec);
        return vec;
    }

    private void addFeature(double[] vec, String token, double weight) {
        int idx = Math.floorMod(token.hashCode(), vec.length);
        vec[idx] += weight;
    }

    private double[] fitDimAndNormalize(double[] input, int dim) {
        double[] result = new double[dim];
        int copy = Math.min(dim, input.length);
        System.arraycopy(input, 0, result, 0, copy);
        normalize(result);
        return result;
    }

    private void normalize(double[] v) {
        double norm = 0;
        for (double d : v) norm += d * d;
        norm = Math.sqrt(norm);
        if (norm == 0) return;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
    }

    private double cosine(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0;
        for (int i = 0; i < n; i++) dot += a[i] * b[i];
        return dot;
    }

    private String serialize(double[] vec) {
        return Arrays.stream(vec).mapToObj(Double::toString).collect(Collectors.joining(","));
    }

    private String toVectorLiteral(double[] vec) {
        return "[" + Arrays.stream(vec).mapToObj(d -> String.format(Locale.ROOT, "%.8f", d)).collect(Collectors.joining(",")) + "]";
    }

    private double[] parse(String serialized) {
        if (serialized == null || serialized.isBlank()) return new double[DIM];
        String[] parts = serialized.split(",");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i]);
        return v;
    }

    private record EmbeddingPayload(double[] vector, String provider) {}
    public record ScoredVectorRecord(VectorRecord record, double score) {}
}
