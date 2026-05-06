package com.example.giga_test;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Table (name = "Task")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {
    @Id
    @Column (name = "id")
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;
    @Column (name = "task_number")
    private String taskNumber;
    private String title;
    private String descriprion;
    private Status status;
    private Priority priority;
    private Category category;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User requester;
    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;
    @Column (name = "created_at")
    private LocalDateTime createdAt;
    @Column (name = "resolution_deadline")
    private LocalDateTime resolutionDeadline;
    @Column (name = "resolution_comment")
    private String resolutionComment;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

}
