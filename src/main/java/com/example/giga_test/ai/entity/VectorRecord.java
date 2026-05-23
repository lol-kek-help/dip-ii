package com.example.giga_test.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vector_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "text_content", nullable = false, length = 8000)
    private String textContent;

    @Column(name = "embedding", nullable = false, length = 8000)
    private String embedding;
}
