package com.example.giga_test.sla.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sla_policies")
@Getter
@Setter
public class SlaPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String categoryCode;
    private String priorityCode;

    @Column(nullable = false)
    private Integer firstResponseMinutes;

    @Column(nullable = false)
    private Integer resolutionMinutes;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
