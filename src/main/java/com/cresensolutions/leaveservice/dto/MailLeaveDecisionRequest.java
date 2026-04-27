package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MailLeaveDecisionRequest(
        @NotBlank(message = "Actor username is required")
        String actorUsername,

        @NotBlank(message = "Decision is required")
        @Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be APPROVED or REJECTED")
        String decision,

        String rejectionReason
) {
}
