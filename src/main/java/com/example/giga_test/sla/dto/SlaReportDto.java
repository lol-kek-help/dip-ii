package com.example.giga_test.sla.dto;

public record SlaReportDto(
        long total,
        long violated,
        double violationRate,
        double avgFrtMinutes,
        double avgMttrMinutes
) {}
