package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AppendAuditTrailRequest(
        @NotBlank(message = "Event is required")
        String event,

        @NotBlank(message = "Actor is required")
        String actor,

        String processInstanceId,
        String taskId,
        String note
) {}
