package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;

public record UserDeletedEvent(
        String email,
        String fullName,
        String username,
        String role,
        String deletedBy,
        String deletedByRole,
        Instant timestamp
) {}
