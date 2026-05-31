package com.example.giga_test.model;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    @Null
    Long id;
    String taskNumber;
    @NotNull
    String title;
    @NotNull
    String description;
    Status status;
    Priority priority;
    Category category;
    @NotNull
    User requester;
    User assignedTo;
    @NotNull
    @PastOrPresent
    LocalDateTime createdAt;
    @FutureOrPresent
    LocalDateTime resolutionDeadline;
    String resolutionComment;
}
