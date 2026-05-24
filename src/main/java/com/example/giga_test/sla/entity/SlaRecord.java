package com.example.giga_test.sla.entity;

import com.example.giga_test.task.entity.TaskEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sla_records")
@Getter
@Setter
public class SlaRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private TaskEntity ticket;

    @ManyToOne
    @JoinColumn(name = "policy_id")
    private SlaPolicy policy;

    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedAt;
    private Long frtMinutes;
    private Long mttrMinutes;

    @Column(nullable = false)
    private boolean violated;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
