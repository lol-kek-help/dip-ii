package com.example.giga_test.ticket.entity;

import com.example.giga_test.model.Status;
import com.example.giga_test.model.User;
import com.example.giga_test.task.entity.TaskEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_status_history")
@Getter
@Setter
public class TicketStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TaskEntity ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private Status toStatus;

    @Column(length = 1000)
    private String reason;

    @ManyToOne
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
