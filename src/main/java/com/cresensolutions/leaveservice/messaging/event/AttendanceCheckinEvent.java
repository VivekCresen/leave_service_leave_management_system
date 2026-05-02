package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;
import java.time.LocalDate;

/** Mirror of the User Service AttendanceEvent — consumed cross-service. */
public record AttendanceCheckinEvent(String username, Instant timestamp, LocalDate date, String type) {}
