package com.cresensolutions.leaveservice.dto;

public record AuditTrailEntryDto(
        String event,
        String actor,
        String timestamp,
        String processInstanceId,
        String taskId,
        String note
) {}
