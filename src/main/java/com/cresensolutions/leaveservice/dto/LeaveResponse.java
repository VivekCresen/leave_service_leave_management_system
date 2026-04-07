package com.cresensolutions.leaveservice.dto;

import java.time.LocalDate;
import java.util.List;

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
        String status,
        String approvedBy,
        String rejectionReason,
        LocalDate createdAt,
        LocalDate updatedAt,
        boolean halfDay,
        String halfDaySession,
        List<Long> notifyUserIds
) {
}
