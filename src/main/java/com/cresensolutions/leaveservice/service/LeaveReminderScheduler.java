package com.cresensolutions.leaveservice.service;

import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class LeaveReminderScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveReminderScheduler.class);

    private static final String APPROVAL_TASK_KEY = "task_manager_approval";
    private static final String REMINDER_4_DAY = "4DAY";
    private static final String REMINDER_2_DAY = "2DAY";

    private final TaskService taskService;
    private final LeaveReminderDispatchService leaveReminderDispatchService;

    public LeaveReminderScheduler(
            TaskService taskService,
            LeaveReminderDispatchService leaveReminderDispatchService
    ) {
        this.taskService = taskService;
        this.leaveReminderDispatchService = leaveReminderDispatchService;
    }

    @Scheduled(cron = "${app.leave-reminder.cron:0 */1 * * * *}")
    @Transactional
    public void processDueReminders() {
        List<Task> approvalTasks = taskService.createTaskQuery()
                .active()
                .taskDefinitionKey(APPROVAL_TASK_KEY)
                .list();

        if (approvalTasks.isEmpty()) {
            LOGGER.debug("[LeaveReminderScheduler] No active Flowable approval tasks found.");
            return;
        }

        Date now = new Date();
        int sent = 0;
        int waiting = 0;
        int skipped = 0;

        for (Task task : approvalTasks) {
            Map<String, Object> variables = taskService.getVariables(task.getId());

            Outcome fourDayOutcome = triggerIfDue(task, variables, "fourDayReminderTime", REMINDER_4_DAY, now);
            sent += fourDayOutcome.sent;
            waiting += fourDayOutcome.waiting;
            skipped += fourDayOutcome.skipped;

            Outcome twoDayOutcome = triggerIfDue(task, variables, "twoDayReminderTime", REMINDER_2_DAY, now);
            sent += twoDayOutcome.sent;
            waiting += twoDayOutcome.waiting;
            skipped += twoDayOutcome.skipped;
        }

        LOGGER.info("[LeaveReminderScheduler] Cron run complete: sent={}, waiting={}, skipped={}, activeTasks={}.",
                sent, waiting, skipped, approvalTasks.size());
    }

    private Outcome triggerIfDue(
            Task task,
            Map<String, Object> variables,
            String timeVariableName,
            String reminderType,
            Date now
    ) {
        Date scheduledTime = asDate(variables.get(timeVariableName));
        if (scheduledTime == null) {
            return Outcome.createSkipped();
        }

        if (scheduledTime.after(now)) {
            return Outcome.createWaiting();
        }

        boolean sent = leaveReminderDispatchService.dispatchReminder(
                asLong(variables.get("leaveId")),
                reminderType,
                asString(variables.get("managerEmail")),
                asString(variables.get("adminEmail")),
                asString(variables.get("employeeName")),
                asString(variables.get("leaveType")),
                asString(variables.get("reason")),
                task.getProcessInstanceId(),
                task.getId()
        );

        if (!sent) {
            LOGGER.debug("[LeaveReminderScheduler] Skipped {} reminder for taskId={} processInstanceId={}.",
                    reminderType, task.getId(), task.getProcessInstanceId());
            return Outcome.createSkipped();
        }

        return Outcome.createSent();
    }

    private Date asDate(Object value) {
        return value instanceof Date date ? date : null;
    }

    private Long asLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue);
        }
        return null;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private record Outcome(int sent, int waiting, int skipped) {
        private static Outcome createSent() {
            return new Outcome(1, 0, 0);
        }

        private static Outcome createWaiting() {
            return new Outcome(0, 1, 0);
        }

        private static Outcome createSkipped() {
            return new Outcome(0, 0, 1);
        }
    }
}
