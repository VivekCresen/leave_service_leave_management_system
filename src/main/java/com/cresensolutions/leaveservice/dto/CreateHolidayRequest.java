package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateHolidayRequest(
        @NotBlank(message = "Holiday name is required")
        String name,

        @NotNull(message = "Holiday date is required")
        LocalDate date,

        String description,

        String createdBy
) {}
