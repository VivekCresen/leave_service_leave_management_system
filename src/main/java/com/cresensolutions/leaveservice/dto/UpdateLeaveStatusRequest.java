package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLeaveStatusRequest(
        @NotBlank(message = "Actor username is required")
        String actorUsername,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "APPROVED|REJECTED|MANAGER_APPROVED", message = "Status must be APPROVED, REJECTED, or MANAGER_APPROVED")
        String status,
        String rejectionReason
) {
}
