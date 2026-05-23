package com.example.giga_test.ai.service;

import com.example.giga_test.ai.entity.VectorRecord;
import com.example.giga_test.ai.repository.VectorRecordRepository;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final int DIM = 128;
    private final VectorRecordRepository vectorRecordRepository;

    public EmbeddingService(VectorRecordRepository vectorRecordRepository) {
        this.vectorRecordRepository = vectorRecordRepository;
    }

    public void upsertTaskEmbedding(TaskEntity taskEntity) {
        String text = (taskEntity.getTitle() + " " + taskEntity.getDescription()).trim();
        upsert("TASK", taskEntity.getId(), text);
    }

    public void upsertKnowledgeEmbedding(Long articleId, String content) {
        upsert("KB", articleId, content);
    }

    public List<ScoredVectorRecord> topK(String sourceType, String query, int k) {
        double[] queryVec = embed(query);
        return vectorRecordRepository.findAllBySourceType(sourceType).stream()
                .map(v -> new ScoredVectorRecord(v, cosine(queryVec, parse(v.getEmbedding()))))
                .sorted(Comparator.comparingDouble(ScoredVectorRecord::score).reversed())
                .limit(k)
                .toList();
    }

    private void upsert(String sourceType, Long sourceId, String text) {
        String vec = serialize(embed(text));
        VectorRecord record = vectorRecordRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseGet(() -> VectorRecord.builder().sourceType(sourceType).sourceId(sourceId).build());
        record.setTextContent(text);
        record.setEmbedding(vec);
        vectorRecordRepository.save(record);
    }

    public double[] embed(String text) {
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

    private double[] parse(String serialized) {
        String[] parts = serialized.split(",");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i]);
        return v;
    }

    public record ScoredVectorRecord(VectorRecord record, double score) {}
}
