package com.example.giga_test.sla.service;

import com.example.giga_test.model.Status;
import com.example.giga_test.sla.dto.SlaReportDto;
import com.example.giga_test.sla.entity.SlaPolicy;
import com.example.giga_test.sla.entity.SlaRecord;
import com.example.giga_test.sla.repository.SlaPolicyRepository;
import com.example.giga_test.sla.repository.SlaRecordRepository;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ticket.repository.TicketStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SlaService {
    private final SlaRecordRepository slaRecordRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TaskRepository taskRepository;
    private final TicketStatusHistoryRepository statusHistoryRepository;

    public SlaService(SlaRecordRepository slaRecordRepository, SlaPolicyRepository slaPolicyRepository,
                      TaskRepository taskRepository, TicketStatusHistoryRepository statusHistoryRepository) {
        this.slaRecordRepository = slaRecordRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.taskRepository = taskRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional
    public SlaRecord ensureForTicket(TaskEntity ticket) {
        return slaRecordRepository.findByTicketId(ticket.getId()).orElseGet(() -> {
            SlaRecord record = new SlaRecord();
            record.setTicket(ticket);
            SlaPolicy policy = slaPolicyRepository.findFirstByPriorityCodeAndActiveTrue(ticket.getPriority() == null ? null : ticket.getPriority().name())
                    .or(() -> slaPolicyRepository.findFirstByActiveTrueOrderByIdAsc())
                    .orElse(null);
            record.setPolicy(policy);
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            record.setViolated(false);
            return slaRecordRepository.save(record);
        });
    }

    @Transactional
    //расчёт sla при смене статуса
    public void onStatusChange(TaskEntity ticket, Status newStatus) {
        SlaRecord record = ensureForTicket(ticket);
        LocalDateTime now = LocalDateTime.now();
        //расчёт первого отклика
        if (record.getFirstResponseAt() == null && (newStatus == Status.ASSIGNED || newStatus == Status.IN_PROGRESS ||
                newStatus == Status.PENDING_USER || newStatus == Status.RESOLVED || newStatus == Status.CLOSED)) {
            record.setFirstResponseAt(now);
            if (ticket.getCreatedAt() != null) {
                record.setFrtMinutes(Duration.between(ticket.getCreatedAt(), now).toMinutes());
            }
        }
        //расчёт времени решения
        if (record.getResolvedAt() == null && (newStatus == Status.RESOLVED || newStatus == Status.CLOSED)) {
            record.setResolvedAt(now);
            if (ticket.getCreatedAt() != null) {
                record.setMttrMinutes(Duration.between(ticket.getCreatedAt(), now).toMinutes());
            }
        }
        //расчёт нарушения
        if (record.getPolicy() != null) {
            boolean frtViolation = record.getFrtMinutes() != null && record.getFrtMinutes() > record.getPolicy().getFirstResponseMinutes();
            boolean mttrViolation = record.getMttrMinutes() != null && record.getMttrMinutes() > record.getPolicy().getResolutionMinutes();
            record.setViolated(frtViolation || mttrViolation);
        }
        record.setUpdatedAt(now);
        slaRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public SlaReportDto report() {
        return report(null, null);
    }

    @Transactional(readOnly = true)
    public SlaReportDto report(LocalDateTime from, LocalDateTime to) {
        List<SlaRecord> records = slaRecordRepository.findAll().stream()
                .filter(r -> from == null || (r.getCreatedAt() != null && !r.getCreatedAt().isBefore(from)))
                .filter(r -> to == null || (r.getCreatedAt() != null && !r.getCreatedAt().isAfter(to)))
                .toList();
        List<TaskEntity> tickets = taskRepository.findAll().stream()
                .filter(t -> from == null || (t.getCreatedAt() != null && !t.getCreatedAt().isBefore(from)))
                .filter(t -> to == null || (t.getCreatedAt() != null && !t.getCreatedAt().isAfter(to)))
                .toList();
        long total = records.size();
        long violated = records.stream().filter(SlaRecord::isViolated).count();
        long closed = tickets.stream().filter(t -> t.getStatus() == Status.CLOSED || t.getStatus() == Status.RESOLVED).count();
        long closedViolated = records.stream().filter(r -> r.isViolated() && r.getTicket() != null && (r.getTicket().getStatus() == Status.CLOSED || r.getTicket().getStatus() == Status.RESOLVED)).count();
        long returned = tickets.stream().filter(t -> t.getStatus() == Status.RETURNED).count() + statusHistoryRepository.countByToStatus(Status.RETURNED);
        long reclassified = statusHistoryRepository.countClassificationChanges();
        long firstContact = records.stream().filter(r -> r.getTicket() != null && (r.getTicket().getStatus() == Status.CLOSED || r.getTicket().getStatus() == Status.RESOLVED))
                .filter(r -> statusHistoryRepository.countByTicketIdAndToStatus(r.getTicket().getId(), Status.RETURNED) == 0)
                .count();

        List<Long> frt = records.stream().map(SlaRecord::getFrtMinutes).filter(Objects::nonNull).sorted().toList();
        List<Long> mttr = records.stream().map(SlaRecord::getMttrMinutes).filter(Objects::nonNull).sorted().toList();
        double avgFrt = frt.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgMttr = mttr.stream().mapToLong(Long::longValue).average().orElse(0);

        return new SlaReportDto(
                total,
                violated,
                percent(violated, total),
                avgFrt,
                avgMttr,
                median(frt),
                median(mttr),
                percentile(frt, 90),
                percentile(frt, 95),
                percentile(mttr, 90),
                percentile(mttr, 95),
                percent(reclassified, tickets.size()),
                percent(returned, tickets.size()),
                percent(closedViolated, closed),
                percent(firstContact, closed),
                group(records, r -> r.getTicket() == null || r.getTicket().getCategory() == null ? "Без категории" : r.getTicket().getCategory().name()),
                group(records, r -> r.getTicket() == null || r.getTicket().getPriority() == null ? "Без приоритета" : r.getTicket().getPriority().name()),
                group(records, r -> r.getTicket() == null || r.getTicket().getAssignedTo() == null ? "Не назначено" : r.getTicket().getAssignedTo().getUsername()),
                daily(records),
                topViolated(records),
                expiringTickets(tickets)
        );
    }

    private List<SlaReportDto.GroupMetric> group(List<SlaRecord> records, Function<SlaRecord, String> classifier) {
        return records.stream().collect(Collectors.groupingBy(classifier)).entrySet().stream()
                .map(e -> {
                    long total = e.getValue().size();
                    long violated = e.getValue().stream().filter(SlaRecord::isViolated).count();
                    double avgFrt = e.getValue().stream().map(SlaRecord::getFrtMinutes).filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(0);
                    double avgMttr = e.getValue().stream().map(SlaRecord::getMttrMinutes).filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(0);
                    return new SlaReportDto.GroupMetric(e.getKey(), total, violated, percent(violated, total), avgFrt, avgMttr);
                })
                .sorted(Comparator.comparing(SlaReportDto.GroupMetric::name))
                .toList();
    }

    private List<SlaReportDto.DailyMetric> daily(List<SlaRecord> records) {
        Map<String, List<SlaRecord>> byDay = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate().toString()));
        return byDay.entrySet().stream()
                .map(e -> {
                    long total = e.getValue().size();
                    long violated = e.getValue().stream().filter(SlaRecord::isViolated).count();
                    return new SlaReportDto.DailyMetric(e.getKey(), total, violated, percent(violated, total));
                })
                .sorted(Comparator.comparing(SlaReportDto.DailyMetric::day))
                .toList();
    }

    private List<SlaReportDto.TopViolatedTicket> topViolated(List<SlaRecord> records) {
        return records.stream().filter(SlaRecord::isViolated)
                .filter(r -> r.getTicket() != null)
                .sorted(Comparator.comparing((SlaRecord r) -> r.getMttrMinutes() == null ? 0 : r.getMttrMinutes()).reversed())
                .limit(10)
                .map(r -> new SlaReportDto.TopViolatedTicket(
                        r.getTicket().getId(),
                        r.getTicket().getTitle(),
                        r.getTicket().getPriority() == null ? null : r.getTicket().getPriority().name(),
                        r.getTicket().getCategory() == null ? null : r.getTicket().getCategory().name(),
                        r.getMttrMinutes() == null ? 0 : r.getMttrMinutes(),
                        r.getPolicy() == null ? 0 : r.getPolicy().getResolutionMinutes()
                ))
                .toList();
    }

    private List<SlaReportDto.ExpiringTicket> expiringTickets(List<TaskEntity> tickets) {
        LocalDateTime now = LocalDateTime.now();
        return tickets.stream()
                .filter(t -> t.getResolutionDeadline() != null)
                .filter(t -> t.getStatus() != Status.CLOSED && t.getStatus() != Status.CANCELED && t.getStatus() != Status.RESOLVED)
                .filter(t -> !t.getResolutionDeadline().isBefore(now))
                .sorted(Comparator.comparing(TaskEntity::getResolutionDeadline))
                .limit(10)
                .map(t -> new SlaReportDto.ExpiringTicket(
                        t.getId(), t.getTitle(),
                        t.getPriority() == null ? null : t.getPriority().name(),
                        t.getCategory() == null ? null : t.getCategory().name(),
                        t.getResolutionDeadline().toString(),
                        t.getAssignedTo() == null ? null : t.getAssignedTo().getUsername()
                ))
                .toList();
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0 : numerator * 100.0 / denominator;
    }

    private double median(List<Long> sorted) {
        if (sorted.isEmpty()) return 0;
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(mid);
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
