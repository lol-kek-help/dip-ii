package com.example.giga_test;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.User;
import com.example.giga_test.sla.entity.SlaPolicy;
import com.example.giga_test.sla.entity.SlaRecord;
import com.example.giga_test.sla.repository.SlaPolicyRepository;
import com.example.giga_test.sla.repository.SlaRecordRepository;
import com.example.giga_test.sla.service.SlaService;
import com.example.giga_test.task.entity.TaskEntity;
import com.example.giga_test.task.repository.TaskRepository;
import com.example.giga_test.ticket.repository.TicketStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlaServiceTest {

    @Test
    void ensureForTicketShouldCreateRecordWithMatchingPolicy() {
        SlaRecordRepository recordRepository = mock(SlaRecordRepository.class);
        SlaPolicyRepository policyRepository = mock(SlaPolicyRepository.class);
        SlaService service = new SlaService(recordRepository, policyRepository, mock(TaskRepository.class), mock(TicketStatusHistoryRepository.class));
        TaskEntity ticket = ticket(10L, Status.NEW, Priority.URGENT, Category.INCIDENT, LocalDateTime.now().minusMinutes(15));
        SlaPolicy policy = policy("URGENT", 10, 60);

        when(recordRepository.findByTicketId(10L)).thenReturn(Optional.empty());
        when(policyRepository.findFirstByPriorityCodeAndActiveTrue("URGENT")).thenReturn(Optional.of(policy));
        when(recordRepository.save(any(SlaRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SlaRecord record = service.ensureForTicket(ticket);

        assertEquals(ticket, record.getTicket());
        assertEquals(policy, record.getPolicy());
        assertFalse(record.isViolated());
        assertNotNull(record.getCreatedAt());
        verify(recordRepository).save(record);
    }

    @Test
    void onStatusChangeShouldSetResponseResolutionAndViolation() {
        SlaRecordRepository recordRepository = mock(SlaRecordRepository.class);
        SlaPolicyRepository policyRepository = mock(SlaPolicyRepository.class);
        SlaService service = new SlaService(recordRepository, policyRepository, mock(TaskRepository.class), mock(TicketStatusHistoryRepository.class));
        TaskEntity ticket = ticket(11L, Status.CLOSED, Priority.HIGH, Category.INCIDENT, LocalDateTime.now().minusMinutes(90));
        SlaPolicy policy = policy("HIGH", 10, 30);
        SlaRecord record = new SlaRecord();
        record.setTicket(ticket);
        record.setPolicy(policy);
        record.setViolated(false);
        record.setCreatedAt(LocalDateTime.now().minusMinutes(90));

        when(recordRepository.findByTicketId(11L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(SlaRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.onStatusChange(ticket, Status.CLOSED);

        assertNotNull(record.getFirstResponseAt());
        assertNotNull(record.getResolvedAt());
        assertTrue(record.getFrtMinutes() >= 89);
        assertTrue(record.getMttrMinutes() >= 89);
        assertTrue(record.isViolated());
        verify(recordRepository).save(record);
    }

    @Test
    void reportShouldCalculateAggregatesAndGroups() {
        SlaRecordRepository recordRepository = mock(SlaRecordRepository.class);
        SlaPolicyRepository policyRepository = mock(SlaPolicyRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        TicketStatusHistoryRepository historyRepository = mock(TicketStatusHistoryRepository.class);
        SlaService service = new SlaService(recordRepository, policyRepository, taskRepository, historyRepository);
        User operator = User.builder().id(5L).username("operator1").name("Operator").role(RoleName.OPERATOR).build();
        TaskEntity closedTicket = ticket(12L, Status.CLOSED, Priority.HIGH, Category.INCIDENT, LocalDateTime.now().minusDays(1));
        closedTicket.setAssignedTo(operator);
        TaskEntity expiringTicket = ticket(13L, Status.IN_PROGRESS, Priority.MEDIUM, Category.GENERAL, LocalDateTime.now().minusHours(2));
        expiringTicket.setResolutionDeadline(LocalDateTime.now().plusHours(3));
        SlaPolicy policy = policy("HIGH", 15, 60);
        SlaRecord violated = record(closedTicket, policy, true, 30L, 120L);
        SlaRecord ok = record(expiringTicket, policy, false, 5L, null);

        when(recordRepository.findAll()).thenReturn(List.of(violated, ok));
        when(taskRepository.findAll()).thenReturn(List.of(closedTicket, expiringTicket));
        when(historyRepository.countByToStatus(Status.RETURNED)).thenReturn(1L);
        when(historyRepository.countClassificationChanges()).thenReturn(1L);
        when(historyRepository.countByTicketIdAndToStatus(12L, Status.RETURNED)).thenReturn(0L);

        var report = service.report();

        assertEquals(2, report.total());
        assertEquals(1, report.violated());
        assertEquals(50.0, report.violationRate());
        assertEquals(17.5, report.avgFrtMinutes());
        assertEquals(120.0, report.avgMttrMinutes());
        assertFalse(report.byCategory().isEmpty());
        assertFalse(report.byPriority().isEmpty());
        assertFalse(report.byOperator().isEmpty());
        assertEquals(1, report.topViolatedTickets().size());
        assertEquals(1, report.expiringTickets().size());
    }

    private SlaRecord record(TaskEntity ticket, SlaPolicy policy, boolean violated, Long frt, Long mttr) {
        SlaRecord record = new SlaRecord();
        record.setTicket(ticket);
        record.setPolicy(policy);
        record.setViolated(violated);
        record.setFrtMinutes(frt);
        record.setMttrMinutes(mttr);
        record.setCreatedAt(ticket.getCreatedAt());
        return record;
    }

    private SlaPolicy policy(String priority, int frt, int resolution) {
        SlaPolicy policy = new SlaPolicy();
        policy.setPriorityCode(priority);
        policy.setFirstResponseMinutes(frt);
        policy.setResolutionMinutes(resolution);
        policy.setActive(true);
        return policy;
    }

    private TaskEntity ticket(Long id, Status status, Priority priority, Category category, LocalDateTime createdAt) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setTitle("Ticket " + id);
        task.setDescription("Description " + id);
        task.setStatus(status);
        task.setPriority(priority);
        task.setCategory(category);
        task.setCreatedAt(createdAt);
        return task;
    }
}
