package com.example.giga_test.ticket.repository;

import com.example.giga_test.ticket.entity.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
    List<AiRecommendation> findAllByTicketIdOrderByCreatedAtDesc(Long ticketId);
    long countByAccepted(Boolean accepted);
}
