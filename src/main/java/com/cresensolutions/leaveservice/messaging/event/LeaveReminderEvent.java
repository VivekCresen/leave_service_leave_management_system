package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;

public record LeaveReminderEvent(
        Long leaveId,
        String reminderType,
        String managerEmail,
        String adminEmail,
        String employeeName,
        String leaveType,
        String reason,
        String processInstanceId,
        String taskId,
        Instant timestamp
) {}
