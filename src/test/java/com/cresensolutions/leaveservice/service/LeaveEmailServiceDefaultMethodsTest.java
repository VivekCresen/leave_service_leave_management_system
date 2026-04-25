package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveEmailServiceDefaultMethodsTest {

    @Test
    void sendPendingApprovalReminder_defaultMethodDelegatesWithNullLeaveId() {
        RecordingLeaveEmailService service = new RecordingLeaveEmailService();
        List<String> recipients = List.of("admin@cresensolutions.com");
        List<LeaveDate> leaveDates = List.of(new LeaveDate(null, LocalDate.of(2026, 4, 28), "FULL"));

        service.sendPendingApprovalReminder(
                recipients,
                "John Doe",
                "Employee",
                "Annual Leave",
                leaveDates,
                "Vacation",
                "Manager One",
                "Manager",
                "https://example.test/login"
        );

        assertThat(service.pendingRecipients).isEqualTo(recipients);
        assertThat(service.pendingEmployeeName).isEqualTo("John Doe");
        assertThat(service.pendingEmployeeRole).isEqualTo("Employee");
        assertThat(service.pendingLeaveType).isEqualTo("Annual Leave");
        assertThat(service.pendingLeaveDates).isEqualTo(leaveDates);
        assertThat(service.pendingReason).isEqualTo("Vacation");
        assertThat(service.pendingManagerName).isEqualTo("Manager One");
        assertThat(service.pendingManagerRole).isEqualTo("Manager");
        assertThat(service.pendingLoginUrl).isEqualTo("https://example.test/login");
        assertThat(service.pendingLeaveId).isNull();
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_defaultMethodDelegatesWithNullLeaveId() {
        RecordingLeaveEmailService service = new RecordingLeaveEmailService();
        List<String> adminRecipients = List.of("admin@cresensolutions.com");
        List<String> employeeRecipients = List.of("john@cresensolutions.com");
        List<LeaveDate> leaveDates = List.of(new LeaveDate(null, LocalDate.of(2026, 4, 28), "MORNING_HALF"));

        service.sendManagerApprovedPendingAdminNotification(
                adminRecipients,
                employeeRecipients,
                "John Doe",
                "Annual Leave",
                leaveDates,
                "Vacation",
                "Manager One"
        );

        assertThat(service.managerApprovedAdminRecipients).isEqualTo(adminRecipients);
        assertThat(service.managerApprovedEmployeeRecipients).isEqualTo(employeeRecipients);
        assertThat(service.managerApprovedEmployeeName).isEqualTo("John Doe");
        assertThat(service.managerApprovedLeaveType).isEqualTo("Annual Leave");
        assertThat(service.managerApprovedLeaveDates).isEqualTo(leaveDates);
        assertThat(service.managerApprovedReason).isEqualTo("Vacation");
        assertThat(service.managerApprovedManagerName).isEqualTo("Manager One");
        assertThat(service.managerApprovedLeaveId).isNull();
    }

    private static final class RecordingLeaveEmailService implements LeaveEmailService {
        private List<String> pendingRecipients;
        private String pendingEmployeeName;
        private String pendingEmployeeRole;
        private String pendingLeaveType;
        private List<LeaveDate> pendingLeaveDates;
        private String pendingReason;
        private String pendingManagerName;
        private String pendingManagerRole;
        private String pendingLoginUrl;
        private Long pendingLeaveId;

        private List<String> managerApprovedAdminRecipients;
        private List<String> managerApprovedEmployeeRecipients;
        private String managerApprovedEmployeeName;
        private String managerApprovedLeaveType;
        private List<LeaveDate> managerApprovedLeaveDates;
        private String managerApprovedReason;
        private String managerApprovedManagerName;
        private Long managerApprovedLeaveId;

        @Override
        public void sendPendingApprovalReminder(
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
        ) {
            this.pendingRecipients = recipients;
            this.pendingEmployeeName = employeeName;
            this.pendingEmployeeRole = employeeRole;
            this.pendingLeaveType = leaveType;
            this.pendingLeaveDates = leaveDates;
            this.pendingReason = reason;
            this.pendingManagerName = managerName;
            this.pendingManagerRole = managerRole;
            this.pendingLoginUrl = loginUrl;
            this.pendingLeaveId = leaveId;
        }

        @Override
        public void sendManagerApprovedPendingAdminNotification(
                List<String> adminRecipients,
                List<String> employeeRecipients,
                String employeeName,
                String leaveType,
                List<LeaveDate> leaveDates,
                String reason,
                String managerName,
                Long leaveId
        ) {
            this.managerApprovedAdminRecipients = adminRecipients;
            this.managerApprovedEmployeeRecipients = employeeRecipients;
            this.managerApprovedEmployeeName = employeeName;
            this.managerApprovedLeaveType = leaveType;
            this.managerApprovedLeaveDates = leaveDates;
            this.managerApprovedReason = reason;
            this.managerApprovedManagerName = managerName;
            this.managerApprovedLeaveId = leaveId;
        }

        @Override
        public void sendLeaveStatusNotification(
                List<String> recipients,
                String employeeName,
                String leaveType,
                List<LeaveDate> leaveDates,
                String reason,
                String status,
                String actionBy,
                String actionByRole,
                String rejectionReason
        ) {
        }

        @Override
        public void sendReminderNotification(
                List<String> recipients,
                String employeeName,
                String leaveType,
                List<LeaveDate> leaveDates,
                String reason,
                String reminderType
        ) {
        }

        @Override
        public void sendPartialLeaveStatusNotification(
                List<String> recipients,
                String employeeName,
                String leaveType,
                List<LeaveDate> approvedDates,
                List<LeaveDate> rejectedDates,
                String reason,
                String actionBy,
                String actionByRole,
                String rejectionReason
        ) {
        }
    }
}
