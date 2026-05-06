package com.cresensolutions.leaveservice.messaging.event;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceCheckinEvent(String username, Instant timestamp, LocalDate date, String type) {}
