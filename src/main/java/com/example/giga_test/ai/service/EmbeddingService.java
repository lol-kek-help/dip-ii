package com.example.giga_test.ai.service;

import com.example.giga_test.ai.entity.VectorRecord;
import com.example.giga_test.ai.repository.VectorRecordRepository;
import com.example.giga_test.integration.LlmClient;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final int DIM = 128;
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
        double[] queryVec = embed(query);
        String vecLiteral = toVectorLiteral(queryVec);
        List<ScoredVectorRecord> fromPgVector = jdbcTemplate.query(
                """
                SELECT id, source_type, source_id, text_content, embedding,
                       1 - (embedding_vector <=> CAST(? AS vector)) AS score
                FROM vector_records
                WHERE source_type = ? AND embedding_vector IS NOT NULL
                ORDER BY embedding_vector <=> CAST(? AS vector)
                LIMIT ?
                """,
                (rs, rowNum) -> mapRecord(rs, rs.getDouble("score")),
                vecLiteral, sourceType, vecLiteral, k
        );
        if (!fromPgVector.isEmpty()) {
            return fromPgVector;
        }

        return vectorRecordRepository.findAllBySourceType(sourceType).stream()
                .map(v -> new ScoredVectorRecord(v, cosine(queryVec, parse(v.getEmbedding()))))
                .sorted(Comparator.comparingDouble(ScoredVectorRecord::score).reversed())
                .limit(k)
                .toList();
    }

    private void upsert(String sourceType, Long sourceId, String text) {
        double[] embeddingArray = embed(text);
        String serialized = serialize(embeddingArray);
        String vecLiteral = toVectorLiteral(embeddingArray);

        jdbcTemplate.update(
                """
                INSERT INTO vector_records(source_type, source_id, text_content, embedding, embedding_vector)
                VALUES (?, ?, ?, ?, CAST(? AS vector))
                ON CONFLICT (source_type, source_id)
                DO UPDATE SET text_content = EXCLUDED.text_content,
                              embedding = EXCLUDED.embedding,
                              embedding_vector = EXCLUDED.embedding_vector
                """,
                sourceType, sourceId, text, serialized, vecLiteral
        );
    }

    private boolean isEmbeddingUpToDate(String sourceType, Long sourceId, String textContent) {
        return vectorRecordRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .map(existing -> Objects.equals(existing.getTextContent(), textContent))
                .orElse(false);
    }

    private ScoredVectorRecord mapRecord(java.sql.ResultSet rs, double score) throws java.sql.SQLException {
        VectorRecord record = VectorRecord.builder()
                .id(rs.getLong("id"))
                .sourceType(rs.getString("source_type"))
                .sourceId(rs.getLong("source_id"))
                .textContent(rs.getString("text_content"))
                .embedding(rs.getString("embedding"))
                .build();
        return new ScoredVectorRecord(record, score);
    }

    public double[] embed(String text) {
        double[] real = llmClient.embed(text);
        if (real != null && real.length > 0) {
            return fitDimAndNormalize(real, DIM);
        }
        return localHashEmbedding(text);
    }

    private double[] localHashEmbedding(String text) {
        double[] vec = new double[DIM];
        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            int idx = Math.abs(token.hashCode()) % DIM;
            vec[idx] += 1.0;
        }
        normalize(vec);
        return vec;
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

    public record ScoredVectorRecord(VectorRecord record, double score) {}
}
