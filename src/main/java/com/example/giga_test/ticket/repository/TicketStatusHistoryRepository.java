package com.example.giga_test.ticket.repository;

import com.example.giga_test.model.Status;
import com.example.giga_test.ticket.entity.TicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {
    List<TicketStatusHistory> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
    long countByToStatus(Status status);
    long countByTicketIdAndToStatus(Long ticketId, Status status);

    @Query("select count(h) from TicketStatusHistory h where h.toStatus = com.example.giga_test.model.Status.RETURNED")
    long countReturnedTransitions();

    @Query("select count(a) from AuditLog a where a.action = 'CLASSIFICATION_UPDATE'")
    long countClassificationChanges();
}
