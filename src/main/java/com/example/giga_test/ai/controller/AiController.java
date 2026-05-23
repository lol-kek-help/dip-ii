package com.example.giga_test.ai.controller;

import com.example.giga_test.ai.dto.AiDtos.*;
import com.example.giga_test.ai.service.AiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
public class AiController {
    private final AiService service;

    public AiController(AiService service) {this.service = service;}

    @PostMapping("/classify")
    public ResponseEntity<ClassifyResponse> classify(@RequestBody @Valid AiRequest request) { return ResponseEntity.ok(service.classify(request.text())); }
    @PostMapping("/similar")
    public ResponseEntity<SimilarResponse> similar(@RequestBody @Valid AiRequest request) { return ResponseEntity.ok(service.similar(request.text())); }
    @PostMapping("/recommend")
    public ResponseEntity<RecommendResponse> recommend(@RequestBody @Valid AiRequest request) { return ResponseEntity.ok(service.recommend(request.text())); }
}
