package com.example.giga_test.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "knowledge_base_articles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class KnowledgeBaseArticle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, length = 5000)
    private String content;
    @Column(nullable = false)
    private String category;
}
