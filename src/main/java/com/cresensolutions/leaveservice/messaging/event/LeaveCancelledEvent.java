package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;

public record LeaveCancelledEvent(
        Long leaveId,
        Long userId,
        String username,
        String leaveType,
        String managerUsername,
        Instant timestamp
) {}
