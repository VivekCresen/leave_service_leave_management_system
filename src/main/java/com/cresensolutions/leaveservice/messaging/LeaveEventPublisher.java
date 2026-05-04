package com.cresensolutions.leaveservice.messaging;

import com.cresensolutions.leaveservice.config.RabbitMQConfig;
import com.cresensolutions.leaveservice.messaging.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;


@Slf4j
@Component
public class LeaveEventPublisher extends BaseEventPublisher {

    public LeaveEventPublisher(RabbitTemplate rabbitTemplate) {
        super(rabbitTemplate);
    }

    public void publishLeaveSubmitted(Long leaveId, Long userId, String username,
                                      String leaveType, List<String> dates,
                                      String managerUsername) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_SUBMITTED,
                new LeaveSubmittedEvent(leaveId, userId, username, leaveType, dates, managerUsername, Instant.now()));
    }

    public void publishManagerApproved(Long leaveId, Long userId, String username,
                                       String employeeEmail, String leaveType,
                                       Integer leaveTypeId, List<String> dates,
                                       String actorUsername, String actorRole) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_MANAGER_APPROVED,
                statusEvent(leaveId, userId, username, employeeEmail, leaveType, leaveTypeId,
                        dates, List.of(), "MANAGER_APPROVED", actorUsername, actorRole, null, 0));
    }

    public void publishLeaveApproved(Long leaveId, Long userId, String username,
                                     String employeeEmail, String leaveType,
                                     Integer leaveTypeId, List<String> dates,
                                     String actorUsername, String actorRole, double days) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_APPROVED,
                statusEvent(leaveId, userId, username, employeeEmail, leaveType, leaveTypeId,
                        dates, List.of(), "APPROVED", actorUsername, actorRole, null, days));
    }

    public void publishLeaveRejected(Long leaveId, Long userId, String username,
                                     String employeeEmail, String leaveType,
                                     Integer leaveTypeId, List<String> dates,
                                     String actorUsername, String actorRole,
                                     String rejectionReason) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_REJECTED,
                statusEvent(leaveId, userId, username, employeeEmail, leaveType, leaveTypeId,
                        dates, List.of(), "REJECTED", actorUsername, actorRole, rejectionReason, 0));
    }

    public void publishPartialDecision(Long leaveId, Long userId, String username,
                                       String employeeEmail, String leaveType,
                                       Integer leaveTypeId, List<String> approvedDates,
                                       List<String> rejectedDates, String actorUsername,
                                       String actorRole, String rejectionReason, double days) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_PARTIAL,
                statusEvent(leaveId, userId, username, employeeEmail, leaveType, leaveTypeId,
                        approvedDates, rejectedDates, "PARTIAL", actorUsername, actorRole, rejectionReason, days));
    }

    public void publishLeaveCancelled(Long leaveId, Long userId, String username,
                                      String leaveType, String managerUsername) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_CANCELLED,
                new LeaveCancelledEvent(leaveId, userId, username, leaveType, managerUsername, Instant.now()));
    }

    public void publishLeaveReminder(Long leaveId, String reminderType,
                                     String managerEmail, String adminEmail,
                                     String employeeName, String leaveType,
                                     String reason, String processInstanceId, String taskId) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_REMINDER,
                new LeaveReminderEvent(leaveId, reminderType, managerEmail, adminEmail,
                        employeeName, leaveType, reason, processInstanceId, taskId, Instant.now()));
    }

    public void publishAdminNotify(Long leaveId, Long userId, String username,
                                   String employeeEmail, String leaveType,
                                   Integer leaveTypeId, List<String> dates,
                                   String actorUsername, String actorRole) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_ADMIN_NOTIFY,
                statusEvent(leaveId, userId, username, employeeEmail, leaveType, leaveTypeId,
                        dates, List.of(), "MANAGER_APPROVED", actorUsername, actorRole, null, 0));
    }

    public void publishBalanceDeduct(Long userId, Integer leaveTypeId, double days, Long leaveId) {
        publish(RabbitMQConfig.LEAVE_EVENTS_EXCHANGE, RabbitMQConfig.RK_LEAVE_BALANCE_DEDUCT,
                new LeaveBalanceDeductEvent(userId, leaveTypeId, days, leaveId, Instant.now()));
    }

    private LeaveStatusEvent statusEvent(Long leaveId, Long userId, String username,
                                         String employeeEmail, String leaveType,
                                         Integer leaveTypeId, List<String> approvedDates,
                                         List<String> rejectedDates, String status,
                                         String actorUsername, String actorRole,
                                         String rejectionReason, double days) {
        return new LeaveStatusEvent(leaveId, userId, username, employeeEmail, leaveType,
                leaveTypeId, approvedDates, rejectedDates, status, actorUsername, actorRole,
                rejectionReason, days, Instant.now());
    }
}
