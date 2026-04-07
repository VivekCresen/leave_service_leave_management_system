package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record UpdateLeaveRequest(
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
        Boolean halfDay,
        String halfDaySession,
        List<Long> notifyUserIds
) {}
