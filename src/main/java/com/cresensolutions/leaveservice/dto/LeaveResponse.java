package com.cresensolutions.leaveservice.dto;

import java.time.OffsetDateTime;
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
        String managerRejectedBy,
        OffsetDateTime managerApprovedAt,
        OffsetDateTime managerRejectedAt,
        String adminApprovedBy,
        String adminRejectedBy,
        OffsetDateTime adminApprovedAt,
        OffsetDateTime adminRejectedAt,
        String rejectionReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<Long> notifyUserIds
) {}
