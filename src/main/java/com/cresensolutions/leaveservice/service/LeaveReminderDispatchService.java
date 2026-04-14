package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.repository.LeaveDateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaveReminderDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveReminderDispatchService.class);

    private final LeaveEmailService leaveEmailService;
    private final LeaveDateRepository leaveDateRepository;

    public LeaveReminderDispatchService(
            LeaveEmailService leaveEmailService,
            LeaveDateRepository leaveDateRepository
    ) {
        this.leaveEmailService = leaveEmailService;
        this.leaveDateRepository = leaveDateRepository;
    }

    /**
     * Dispatches a reminder email (4DAY or 2DAY) to manager/admin.
     *
     * @return true if email was sent, false if skipped
     */
    public boolean dispatchReminder(
            Long leaveId,
            String reminderType,
            String managerEmail,
            String adminEmail,
            String employeeName,
            String leaveType,
            String reason,
            String processInstanceId,
            String taskId
    ) {
        List<String> recipients = buildRecipients(managerEmail, adminEmail);
        if (recipients.isEmpty()) {
            LOGGER.warn("[LeaveReminderDispatchService] No recipients for {} reminder, leaveId={}", reminderType, leaveId);
            return false;
        }

        List<LeaveDate> dates = leaveId != null
                ? leaveDateRepository.findByApplicationId(leaveId)
                : List.of();

        LOGGER.info("[LeaveReminderDispatchService] Dispatching {} reminder for leaveId={} to {} recipient(s)",
                reminderType, leaveId, recipients.size());

        leaveEmailService.sendPendingApprovalReminder(
                recipients,
                employeeName != null ? employeeName : "Employee",
                leaveType != null ? leaveType : "",
                dates,
                reason != null ? reason : ""
        );

        return true;
    }

    private List<String> buildRecipients(String managerEmail, String adminEmail) {
        List<String> list = new ArrayList<>();
        if (managerEmail != null && !managerEmail.isBlank()) list.add(managerEmail);
        if (adminEmail != null && !adminEmail.isBlank() && !adminEmail.equals(managerEmail)) list.add(adminEmail);
        return list;
    }
}