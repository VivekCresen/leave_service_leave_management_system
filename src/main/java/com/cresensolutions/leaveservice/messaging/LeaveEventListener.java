package com.cresensolutions.leaveservice.messaging;

import com.cresensolutions.leaveservice.config.RabbitMQConfig;
import com.cresensolutions.leaveservice.messaging.event.*;
import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.repository.LeaveDateRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.service.LeaveBalanceService;
import com.cresensolutions.leaveservice.service.LeaveEmailService;
import com.cresensolutions.leaveservice.service.LeaveReminderDispatchService;
import com.cresensolutions.leaveservice.model.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes all leave-domain events from RabbitMQ.
 *
 * Email delivery and balance deduction are fully decoupled from the
 * request thread. If SMTP or the DB is temporarily unavailable, the
 * message stays in the queue and retries automatically (up to 3 times
 * per the listener container config, then moves to DLQ).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveEventListener {

    private final LeaveEmailService leaveEmailService;
    private final LeaveReminderDispatchService leaveReminderDispatchService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveDateRepository leaveDateRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserProfileRepository userProfileRepository;

    // ── Leave submitted → notify manager ─────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_SUBMITTED)
    public void onLeaveSubmitted(LeaveSubmittedEvent event) {
        log.info("[Leave] Submitted: leaveId={} user={} type={}", event.leaveId(), event.username(), event.leaveType());
        // Extend here: push SSE to manager's browser via User Service cross-service call,
        // or publish a cross-service event to user.events exchange
    }

    // ── Manager approved → notify admin + employee ────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_MANAGER_APPROVED)
    public void onManagerApproved(LeaveStatusEvent event) {
        log.info("[Leave] Manager approved: leaveId={}", event.leaveId());
        List<LeaveDate> dates = leaveDateRepository.findByApplicationId(event.leaveId());
        List<String> adminEmails = userProfileRepository.findActiveByRole("ADMIN").stream()
                .map(UserProfile::getEmailId).filter(e -> e != null && !e.isBlank()).toList();
        List<String> employeeEmails = event.employeeEmail() != null ? List.of(event.employeeEmail()) : List.of();

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                adminEmails, employeeEmails,
                resolveEmployeeName(event.userId()),
                event.leaveType(), dates, "",
                event.actorUsername(), event.leaveId());
    }

    // ── Admin approved → notify employee + deduct balance ────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_APPROVED)
    public void onLeaveApproved(LeaveStatusEvent event) {
        log.info("[Leave] Approved: leaveId={} days={}", event.leaveId(), event.days());
        List<LeaveDate> dates = leaveDateRepository.findByApplicationId(event.leaveId());
        List<String> recipients = event.employeeEmail() != null ? List.of(event.employeeEmail()) : List.of();

        leaveEmailService.sendLeaveStatusNotification(
                recipients, resolveEmployeeName(event.userId()),
                event.leaveType(), dates, "", "APPROVED",
                event.actorUsername(), event.actorRole(), null);
    }

    // ── Rejected → notify employee ────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_REJECTED)
    public void onLeaveRejected(LeaveStatusEvent event) {
        log.info("[Leave] Rejected: leaveId={}", event.leaveId());
        List<LeaveDate> dates = leaveDateRepository.findByApplicationId(event.leaveId());
        List<String> recipients = event.employeeEmail() != null ? List.of(event.employeeEmail()) : List.of();

        leaveEmailService.sendLeaveStatusNotification(
                recipients, resolveEmployeeName(event.userId()),
                event.leaveType(), dates, "", "REJECTED",
                event.actorUsername(), event.actorRole(), event.rejectionReason());
    }

    // ── Partial decision → notify employee ───────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_PARTIAL)
    public void onPartialDecision(LeaveStatusEvent event) {
        log.info("[Leave] Partial decision: leaveId={} approved={} rejected={}",
                event.leaveId(), event.approvedDates().size(), event.rejectedDates().size());
        // Extend here: send partial notification email using approvedDates + rejectedDates
    }

    // ── Cancelled → notify manager ────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_CANCELLED)
    public void onLeaveCancelled(LeaveCancelledEvent event) {
        log.info("[Leave] Cancelled: leaveId={} user={}", event.leaveId(), event.username());
        // Extend here: notify manager that the leave was cancelled,
        // publish cross-service event so User Service can update SSE state
    }

    // ── Reminder → dispatch via existing service ──────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_REMINDER)
    public void onLeaveReminder(LeaveReminderEvent event) {
        log.info("[Leave] Reminder: leaveId={} type={}", event.leaveId(), event.reminderType());
        leaveReminderDispatchService.dispatchReminder(
                event.leaveId(), event.reminderType(),
                event.managerEmail(), event.adminEmail(),
                event.employeeName(), event.leaveType(),
                event.reason(), event.processInstanceId(), event.taskId());
    }

    // ── Admin notify (from Flowable) ──────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_ADMIN_NOTIFY)
    public void onAdminNotify(LeaveStatusEvent event) {
        log.info("[Leave] Admin notify: leaveId={}", event.leaveId());
        List<LeaveDate> dates = leaveDateRepository.findByApplicationId(event.leaveId());
        List<String> adminEmails = userProfileRepository.findActiveByRole("ADMIN").stream()
                .map(UserProfile::getEmailId).filter(e -> e != null && !e.isBlank()).toList();
        List<String> employeeEmails = event.employeeEmail() != null ? List.of(event.employeeEmail()) : List.of();

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                adminEmails, employeeEmails,
                resolveEmployeeName(event.userId()),
                event.leaveType(), dates, "",
                event.actorUsername(), event.leaveId());
    }

    // ── Balance deduction (with DLQ safety net) ───────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.Q_LEAVE_BALANCE_DEDUCT)
    public void onBalanceDeduct(LeaveBalanceDeductEvent event) {
        log.info("[Leave] Balance deduct: userId={} leaveTypeId={} days={}",
                event.userId(), event.leaveTypeId(), event.days());
        leaveBalanceService.deductLeaveBalance(event.userId(), event.leaveTypeId(), event.days());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveEmployeeName(Long userId) {
        if (userId == null) return "Employee";
        return userProfileRepository.findById(userId)
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank()
                        ? u.getFullName() : u.getUserName())
                .orElse("Employee");
    }
}
