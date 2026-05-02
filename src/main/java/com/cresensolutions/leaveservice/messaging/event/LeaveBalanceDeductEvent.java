package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;

public record LeaveBalanceDeductEvent(
        Long userId,
        Integer leaveTypeId,
        double days,
        Long leaveId,
        Instant timestamp
) {}
