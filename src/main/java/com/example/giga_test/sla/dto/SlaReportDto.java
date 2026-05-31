package com.example.giga_test.sla.dto;

import java.util.List;

public record SlaReportDto(
        long total,
        long violated,
        double violationRate,
        double avgFrtMinutes,
        double avgMttrMinutes,
        double medianFrtMinutes,
        double medianMttrMinutes,
        double p90FrtMinutes,
        double p95FrtMinutes,
        double p90MttrMinutes,
        double p95MttrMinutes,
        double misclassificationRate,
        double returnedRate,
        double slaViolationClosedRate,
        double fcrRate,
        List<GroupMetric> byCategory,
        List<GroupMetric> byPriority,
        List<GroupMetric> byOperator,
        List<DailyMetric> daily,
        List<TopViolatedTicket> topViolatedTickets,
        List<ExpiringTicket> expiringTickets
) {
    public record GroupMetric(String name, long total, long violated, double violationRate, double avgFrtMinutes, double avgMttrMinutes) {}
    public record DailyMetric(String day, long total, long violated, double violationRate) {}
    public record TopViolatedTicket(Long ticketId, String title, String priority, String category, long mttrMinutes, long allowedMinutes) {}
    public record ExpiringTicket(Long ticketId, String title, String priority, String category, String deadline, String assignedTo) {}
}
