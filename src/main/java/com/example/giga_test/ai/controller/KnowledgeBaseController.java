package com.example.giga_test.ai.controller;

import com.example.giga_test.ai.entity.KnowledgeBaseArticle;
import com.example.giga_test.ai.repository.KnowledgeBaseArticleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeBaseController {
    private final KnowledgeBaseArticleRepository repository;

    public KnowledgeBaseController(KnowledgeBaseArticleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<KnowledgeBaseArticle> list(@RequestParam(name = "q", required = false) String q) {
        var all = repository.findAll();
        if (q == null || q.isBlank()) return all;
        String needle = q.toLowerCase();
        return all.stream().filter(a ->
                a.getTitle().toLowerCase().contains(needle) ||
                        a.getContent().toLowerCase().contains(needle) ||
                        a.getCategory().toLowerCase().contains(needle)
        ).toList();
    }

    @GetMapping("/{id}")
    public KnowledgeBaseArticle getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Knowledge article not found: " + id));
    }
}
