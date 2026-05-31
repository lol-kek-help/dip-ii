package com.example.giga_test.task.dto;

import com.example.giga_test.model.Status;
import jakarta.validation.constraints.NotNull;

public class TicketLifecycleDtos {
    public record ChangeStatusRequest(@NotNull Status status, String reason) {}
    public record AssignRequest(@NotNull Long assigneeId) {}
    public record EscalateRequest(@jakarta.validation.constraints.NotBlank String reason) {}
    public record CloseRequest(@jakarta.validation.constraints.NotBlank String resolutionComment) {}
}
