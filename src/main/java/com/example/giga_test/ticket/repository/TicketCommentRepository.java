package com.example.giga_test.ticket.repository;

import com.example.giga_test.ticket.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
