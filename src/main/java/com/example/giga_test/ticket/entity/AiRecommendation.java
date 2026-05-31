package com.example.giga_test.ticket.entity;

import com.example.giga_test.model.User;
import com.example.giga_test.task.entity.TaskEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendations")
@Getter
@Setter
public class AiRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TaskEntity ticket;

    @Column(nullable = false, length = 8000)
    private String recommendation;

    @Column(name = "steps", length = 4000)
    private String stepsJson;

    @Column(name = "mode", length = 64)
    private String mode;

    @Column(name = "sources", length = 1000)
    private String sourcesJson;

    @Column(name = "llm_status", length = 128)
    private String llmStatus;

    @Column(name = "raw_model_output", length = 12000)
    private String rawModelOutput;

    @Column
    private Boolean accepted;

    @Column(name = "usefulness_score")
    private Integer usefulnessScore;

    @Column(name = "feedback_comment", length = 1000)
    private String feedbackComment;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "evaluated_by_user_id")
    private User evaluatedByUser;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;
}
