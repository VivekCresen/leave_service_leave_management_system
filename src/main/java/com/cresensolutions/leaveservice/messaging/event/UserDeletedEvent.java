package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;

/** Mirror of the User Service UserDeletedEvent — consumed cross-service. */
public record UserDeletedEvent(
        String email,
        String fullName,
        String username,
        String role,
        String deletedBy,
        String deletedByRole,
        Instant timestamp
) {}
