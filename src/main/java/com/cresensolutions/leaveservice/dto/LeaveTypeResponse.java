package com.cresensolutions.leaveservice.dto;

import java.time.Instant;

public record LeaveTypeResponse(
        Integer id,
        String leaveName,
        String leaveUniqueName,
        String description,
        Integer maxDays,
        String genderRestriction,
        Instant createdAt,
        Instant updatedAt
) {
}
