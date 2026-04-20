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
        List<LeaveDateDto> leaveDates,
        String reason,
        String comments,
        String trail,
        boolean editable,
        String status,
        String approvedBy,
        String managerApprovedBy,
        String rejectionReason,
        LocalDate createdAt,
        LocalDate updatedAt,
        List<Long> notifyUserIds
) {}
