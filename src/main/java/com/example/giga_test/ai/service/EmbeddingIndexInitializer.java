package com.example.giga_test.ai.service;

import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import com.example.giga_test.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexInitializer.class);

    private final TaskRepository taskRepository;
    private final KnowledgeBaseArticleRepository articleRepository;
    private final EmbeddingService embeddingService;

    public EmbeddingIndexInitializer(TaskRepository taskRepository, KnowledgeBaseArticleRepository articleRepository, EmbeddingService embeddingService) {
        this.taskRepository = taskRepository;
        this.articleRepository = articleRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int taskCount = 0;
        for (var task : taskRepository.findAll()) {
            embeddingService.upsertTaskEmbedding(task);
            taskCount++;
        }

        int articleCount = 0;
        for (var article : articleRepository.findAll()) {
            String text = (article.getTitle() + "\n" + article.getCategory() + "\n" + article.getContent()).trim();
            embeddingService.upsertKnowledgeEmbedding(article.getId(), text);
            articleCount++;
        }

        log.info("Embedding index synchronized: tasks={}, knowledgeBaseArticles={}", taskCount, articleCount);
    }
}
