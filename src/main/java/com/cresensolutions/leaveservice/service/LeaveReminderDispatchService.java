package com.cresensolutions.leaveservice.service;

public interface LeaveReminderDispatchService {

    boolean dispatchReminder(
            Long leaveId,
            String reminderType,
            String managerEmail,
            String adminEmail,
            String employeeName,
            String leaveType,
            String reason,
            String processInstanceId,
            String taskId
    );
}
