package com.cresensolutions.leaveservice.dto;

import java.time.LocalDate;

public record LeaveResponse(
        Long id,
        Long userId,
        String fullName,
        String emailId,
        Integer leaveTypeId,
        String leaveType,
        LocalDate fromDate,
        LocalDate toDate,
        String reason,
        String comments,
        String trail,
        boolean editable,
        LocalDate createdAt,
        LocalDate updatedAt
) {
}
