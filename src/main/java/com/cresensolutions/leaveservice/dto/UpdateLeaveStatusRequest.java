package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLeaveStatusRequest(
        @NotBlank(message = "Actor username is required")
        String actorUsername,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "APPROVED|REJECTED", message = "Status must be APPROVED or REJECTED")
        String status,

        // Required only when status is REJECTED
        String rejectionReason
) {
}
