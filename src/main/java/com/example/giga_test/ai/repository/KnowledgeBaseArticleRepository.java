package com.example.giga_test.ai.repository;

import com.example.giga_test.ai.entity.KnowledgeBaseArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseArticleRepository extends JpaRepository<KnowledgeBaseArticle, Long> {}
