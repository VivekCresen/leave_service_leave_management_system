package com.cresensolutions.leaveservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLeaveTypeRequest(
        @NotBlank(message = "Leave name is required")
        @Size(max = 100, message = "Leave name must be at most 100 characters")
        String leaveName,

        @NotBlank(message = "Leave unique name is required")
        @Size(max = 100, message = "Leave unique name must be at most 100 characters")
        String leaveUniqueName,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @Min(value = 1, message = "Max days must be at least 1")
        @Max(value = 365, message = "Max days must be at most 365")
        Integer maxDays,
        
        @Pattern(regexp = "MALE|FEMALE", message = "Gender restriction must be MALE or FEMALE")
        String genderRestriction
) {
}
