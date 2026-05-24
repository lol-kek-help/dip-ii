package com.example.giga_test.sla.repository;

import com.example.giga_test.sla.entity.SlaRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaRecordRepository extends JpaRepository<SlaRecord, Long> {
    Optional<SlaRecord> findByTicketId(Long ticketId);
}
