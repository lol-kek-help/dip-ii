package com.example.giga_test.ai.repository;

import com.example.giga_test.ai.entity.VectorRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VectorRecordRepository extends JpaRepository<VectorRecord, Long> {
    List<VectorRecord> findAllBySourceType(String sourceType);
    Optional<VectorRecord> findBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
