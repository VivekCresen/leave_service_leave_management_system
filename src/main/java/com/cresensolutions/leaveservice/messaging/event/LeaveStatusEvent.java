package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;
import java.util.List;

/**
 * Generic leave status change event — used for approved, rejected,
 * manager-approved, and partial-decision notifications.
 */
public record LeaveStatusEvent(
        Long leaveId,
        Long userId,
        String username,
        String employeeEmail,
        String leaveType,
        Integer leaveTypeId,
        List<String> approvedDates,
        List<String> rejectedDates,
        String status,
        String actorUsername,
        String actorRole,
        String rejectionReason,
        double days,
        Instant timestamp
) {}
