package com.cresensolutions.leaveservice.dto;

public record LeaveTypeResponse(
        Integer id,
        String leaveName,
        String leaveUniqueName,
        String description,
        Integer maxDays
) {
}
