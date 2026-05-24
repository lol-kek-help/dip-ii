package com.example.giga_test.sla.service;

import com.example.giga_test.model.Status;
import com.example.giga_test.sla.dto.SlaReportDto;
import com.example.giga_test.sla.entity.SlaPolicy;
import com.example.giga_test.sla.entity.SlaRecord;
import com.example.giga_test.sla.repository.SlaPolicyRepository;
import com.example.giga_test.sla.repository.SlaRecordRepository;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SlaService {
    private final SlaRecordRepository slaRecordRepository;
    private final SlaPolicyRepository slaPolicyRepository;

    public SlaService(SlaRecordRepository slaRecordRepository, SlaPolicyRepository slaPolicyRepository) {
        this.slaRecordRepository = slaRecordRepository;
        this.slaPolicyRepository = slaPolicyRepository;
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
    public void onStatusChange(TaskEntity ticket, Status newStatus) {
        SlaRecord record = ensureForTicket(ticket);
        LocalDateTime now = LocalDateTime.now();

        if (record.getFirstResponseAt() == null && (newStatus == Status.ASSIGNED || newStatus == Status.IN_PROGRESS || newStatus == Status.PENDING_USER || newStatus == Status.RESOLVED || newStatus == Status.CLOSED)) {
            record.setFirstResponseAt(now);
            if (ticket.getCreatedAt() != null) {
                record.setFrtMinutes(Duration.between(ticket.getCreatedAt(), now).toMinutes());
            }
        }

        if (record.getResolvedAt() == null && (newStatus == Status.RESOLVED || newStatus == Status.CLOSED)) {
            record.setResolvedAt(now);
            if (ticket.getCreatedAt() != null) {
                record.setMttrMinutes(Duration.between(ticket.getCreatedAt(), now).toMinutes());
            }
        }

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
        List<SlaRecord> records = slaRecordRepository.findAll();
        long total = records.size();
        long violated = records.stream().filter(SlaRecord::isViolated).count();
        double avgFrt = records.stream().filter(r -> r.getFrtMinutes() != null).mapToLong(SlaRecord::getFrtMinutes).average().orElse(0);
        double avgMttr = records.stream().filter(r -> r.getMttrMinutes() != null).mapToLong(SlaRecord::getMttrMinutes).average().orElse(0);
        double rate = total == 0 ? 0 : (violated * 100.0 / total);
        return new SlaReportDto(total, violated, rate, avgFrt, avgMttr);
    }
}
