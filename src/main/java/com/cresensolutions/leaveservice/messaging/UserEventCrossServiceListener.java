package com.cresensolutions.leaveservice.messaging;

import com.cresensolutions.leaveservice.config.RabbitMQConfig;
import com.cresensolutions.leaveservice.messaging.event.AttendanceCheckinEvent;
import com.cresensolutions.leaveservice.messaging.event.UserDeletedEvent;
import com.cresensolutions.leaveservice.repository.LeaveNotifyUserRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Consumes cross-service events published by User Service.
 *
 * #19 — user.deleted → soft-deactivate the UserProfile read-model in Leave Service
 *        and cancel any pending leaves for that user so the approval queue stays clean.
 *
 * #20 — attendance.checkin → cross-check whether the user has an approved leave
 *        on the same date and flag it for HR review.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventCrossServiceListener {

    private final UserProfileRepository userProfileRepository;
    private final LeaveRepository leaveRepository;
    private final LeaveNotifyUserRepository leaveNotifyUserRepository;

    // ── #19 user.deleted → Leave Service cleanup ──────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_USER_DELETED_LEAVE)
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("[CrossService] User deleted: username={}", event.username());

        if (event.username() == null || event.username().isBlank()) return;

        // Mark the UserProfile read-model as inactive so it no longer appears in
        // leave lookups, notify-user lists, or approver resolution
        userProfileRepository.findByUserNameIgnoreCase(event.username()).ifPresent(profile -> {
            profile.deactivate();
            userProfileRepository.save(profile);
            log.info("[CrossService] Deactivated UserProfile for deleted user: {}", event.username());
        });

        // Cancel any PENDING leaves — the manager no longer needs to action them
        List<LeaveRecord> pendingLeaves = leaveRepository.findPendingByUsername(event.username());
        if (!pendingLeaves.isEmpty()) {
            pendingLeaves.forEach(leave -> {
                leave.setManagerRejected("SYSTEM",
                        "Auto-cancelled: user account deleted by " + event.deletedBy());
                leave.appendTrailEntry("CANCELLED", "SYSTEM", null, null,
                        "Auto-cancelled: user account deleted by " + event.deletedBy());
            });
            leaveRepository.saveAll(pendingLeaves);
            log.info("[CrossService] Cancelled {} pending leave(s) for deleted user: {}",
                    pendingLeaves.size(), event.username());
        }

        // Remove the deleted user from all notify-user lists to prevent orphaned references
        userProfileRepository.findByUserNameIgnoreCase(event.username())
                .map(UserProfile::getId)
                .ifPresent(userId -> {
                    leaveNotifyUserRepository.deleteByUserId(userId);
                    log.info("[CrossService] Removed notify-user entries for userId={}", userId);
                });
    }

    // ── #20 attendance.checkin → approved-leave cross-check ──────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_ATTENDANCE_CHECKIN_LEAVE)
    @Transactional(readOnly = true)
    public void onAttendanceCheckin(AttendanceCheckinEvent event) {
        if (event.username() == null || event.date() == null) return;

        LocalDate date = event.date();
        boolean hasApprovedLeave = leaveRepository
                .existsApprovedLeaveOnDate(event.username(), date);

        if (hasApprovedLeave) {
            // Flag for HR review — extend here: publish a leave.attendance.conflict event,
            // write to an audit table, or push an SSE alert to the admin dashboard
            log.warn("[CrossService] Attendance conflict: user={} checked in on {} but has an approved leave",
                    event.username(), date);
        }
    }
}
