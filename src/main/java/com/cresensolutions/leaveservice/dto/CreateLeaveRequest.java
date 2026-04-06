package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateLeaveRequest(
        // Either userId or username must be provided
        Long userId,

        String username,

        @NotNull(message = "Leave type id is required")
        Integer leaveTypeId,

        @NotNull(message = "From date is required")
        LocalDate fromDate,

        @NotNull(message = "To date is required")
        LocalDate toDate,

        @NotBlank(message = "Reason is required")
        String reason,

        String comments,
        String trail,
        Boolean editable
) {
}
