package com.example.giga_test.audit.entity;

import com.example.giga_test.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    private String entityId;

    @Column(length = 8000)
    private String details;

    @Column(name = "before_value", length = 8000)
    private String beforeValue;

    @Column(name = "after_value", length = 8000)
    private String afterValue;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
