package com.cresensolutions.leaveservice.dto;

public record NotifyUserResponse(
        Long id,
        String fullName,
        String emailId,
        String role
) {}
