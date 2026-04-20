package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record PartialLeaveStatusRequest(
        @NotBlank(message = "Actor username is required")
        String actorUsername,

        @NotEmpty(message = "At least one date decision is required")
        List<DateDecision> dateDecisions,

        String rejectionReason
) {
    public record DateDecision(
            LocalDate date,
            String dayType,
            // "APPROVED" or "REJECTED"
            String status
    ) {}
}
