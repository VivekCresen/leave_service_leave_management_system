package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;
import java.util.List;

public record LeaveSubmittedEvent(
        Long leaveId,
        Long userId,
        String username,
        String leaveType,
        List<String> dates,
        String managerUsername,
        Instant timestamp
) {}
