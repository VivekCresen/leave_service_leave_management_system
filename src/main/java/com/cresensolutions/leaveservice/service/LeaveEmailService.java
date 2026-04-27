package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;

import java.util.List;

public interface LeaveEmailService {

    default void sendPendingApprovalReminder(
            List<String> recipients,
            String employeeName,
            String employeeRole,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String managerName,
            String managerRole,
            String loginUrl
    ) {
        sendPendingApprovalReminder(recipients, employeeName, employeeRole, leaveType,
                leaveDates, reason, managerName, managerRole, loginUrl, null);
    }

    void sendPendingApprovalReminder(
            List<String> recipients,
            String employeeName,
            String employeeRole,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String managerName,
            String managerRole,
            String loginUrl,
            Long leaveId
    );

    default void sendManagerApprovedPendingAdminNotification(
            List<String> adminRecipients,
            List<String> employeeRecipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String managerName
    ) {
        sendManagerApprovedPendingAdminNotification(adminRecipients, employeeRecipients,
                employeeName, leaveType, leaveDates, reason, managerName, null);
    }

    void sendManagerApprovedPendingAdminNotification(
            List<String> adminRecipients,
            List<String> employeeRecipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String managerName,
            Long leaveId
    );

    void sendLeaveStatusNotification(
            List<String> recipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String status,
            String actionBy,
            String actionByRole,
            String rejectionReason
    );

    void sendReminderNotification(
            List<String> recipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String reminderType
    );


    void sendPartialLeaveStatusNotification(
            List<String> recipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> approvedDates,
            List<LeaveDate> rejectedDates,
            String reason,
            String actionBy,
            String actionByRole,
            String rejectionReason
    );
}
