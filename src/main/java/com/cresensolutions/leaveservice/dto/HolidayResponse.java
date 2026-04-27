package com.cresensolutions.leaveservice.dto;

import java.time.Instant;
import java.time.LocalDate;

public record HolidayResponse(
        Long id,
        String name,
        LocalDate date,
        String description,
        String createdBy,
        Instant createdAt
) {}
