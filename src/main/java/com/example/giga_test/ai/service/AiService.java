package com.example.giga_test.ai.service;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.integration.LlmClient;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiService {
    private final LlmClient llmClient;
    private final TaskRepository taskRepository;
    private final KnowledgeBaseArticleRepository articleRepository;

    public AiService(LlmClient llmClient, TaskRepository taskRepository, KnowledgeBaseArticleRepository articleRepository) {
        this.llmClient = llmClient;
        this.taskRepository = taskRepository;
        this.articleRepository = articleRepository;
    }

    public ClassifyResponse classify(String text) {
        String lc = text.toLowerCase();
        String category = lc.contains("доступ") ? "ACCESS" : lc.contains("инцид") ? "INCIDENT" : "GENERAL";
        String priority = lc.contains("крит") || lc.contains("простой") ? "URGENT" : "MEDIUM";
        return new ClassifyResponse(category, priority, llmClient.ask("Classify: "+text));
    }

    public SimilarResponse similar(String text) {
        var taskItems = taskRepository.findTop5ByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(text, text).stream()
                .map(t -> new SimilarItem(t.getId(), t.getTitle(), score(text, t.getTitle()+" "+t.getDescription())))
                .sorted(Comparator.comparingDouble(SimilarItem::score).reversed()).toList();
        var articles = articleRepository.findAll().stream().map(a -> a.getTitle()).limit(3).toList();
        return new SimilarResponse(taskItems, articles);
    }

    public RecommendResponse recommend(String text) {
        var sim = similar(text);
        return new RecommendResponse(llmClient.ask("Give support recommendation for: "+text),
                List.of("Проверить похожие кейсы: "+sim.tickets().size(), "Проверить права доступа", "Подтвердить SLA и эскалацию"));
    }

    private double score(String a, String b) {
        Set<String> sa = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\s+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\s+")));
        if (sa.isEmpty()) return 0;
        long common = sa.stream().filter(sb::contains).count();
        return common * 1.0 / sa.size();
    }
}
