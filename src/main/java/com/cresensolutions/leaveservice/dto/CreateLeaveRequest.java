package com.cresensolutions.leaveservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateLeaveRequest(
        Long userId,

        String username,

        @NotNull(message = "Leave type id is required")
        Integer leaveTypeId,

        @NotEmpty(message = "At least one leave date is required")
        @Valid
        List<LeaveDateDto> leaveDates,

        @NotBlank(message = "Reason is required")
        String reason,

        String comments,
        String trail,
        Boolean editable,
        List<Long> notifyUserIds
) {}
