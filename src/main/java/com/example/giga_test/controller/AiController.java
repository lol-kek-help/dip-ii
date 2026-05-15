package com.example.giga_test.controller;

import com.example.giga_test.service.AiService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/task/ai")
public class AiController {
    private final AiService service;

    public AiController(AiService service) {
        this.service = service;
    }
}
