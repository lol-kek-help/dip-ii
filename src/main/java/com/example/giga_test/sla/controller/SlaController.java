package com.example.giga_test.sla.controller;

import com.example.giga_test.sla.dto.SlaReportDto;
import com.example.giga_test.sla.service.SlaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sla")
public class SlaController {
    private final SlaService slaService;

    public SlaController(SlaService slaService) {
        this.slaService = slaService;
    }

    @GetMapping("/report")
    public ResponseEntity<SlaReportDto> report() {
        return ResponseEntity.ok(slaService.report());
    }
}
