package com.example.giga_test.sla.repository;

import com.example.giga_test.sla.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
    Optional<SlaPolicy> findFirstByPriorityCodeAndActiveTrue(String priorityCode);
    Optional<SlaPolicy> findFirstByActiveTrueOrderByIdAsc();
}
