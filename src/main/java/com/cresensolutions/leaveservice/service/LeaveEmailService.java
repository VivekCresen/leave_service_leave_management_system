package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LeaveEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveEmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String fromAddress;

    @Value("${app.mail.logo-path:}")
    private String logoPath;

    @Value("${app.mail.batch-size:25}")
    private int batchSize;

    public LeaveEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPendingApprovalReminder(
            List<String> recipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason
    ) {
        if (fromAddress == null || fromAddress.isBlank()) {
            LOGGER.warn("[LeaveEmailService] fromAddress not configured, skipping pending approval reminder.");
            return;
        }
        if (recipients == null || recipients.isEmpty()) {
            LOGGER.warn("[LeaveEmailService] No recipients for pending approval reminder.");
            return;
        }
        String subject = "Leave Approval Required: " + employeeName;
        String body = buildPendingApprovalBody(employeeName, leaveType, leaveDates, reason);
        sendInBatches(recipients, subject, body);
    }

    public void sendLeaveStatusNotification(
            List<String> recipients,
            String employeeName,
            String leaveType,
            List<LeaveDate> leaveDates,
            String reason,
            String status,
            String actionBy,
            String rejectionReason
    ) {
        if (fromAddress == null || fromAddress.isBlank()) {
            LOGGER.warn("[LeaveEmailService] fromAddress not configured, skipping status notification.");
            return;
        }
        if (recipients == null || recipients.isEmpty()) {
            LOGGER.warn("[LeaveEmailService] No recipients for status notification.");
            return;
        }
        String subject = "Leave " + capitalize(status) + ": " + employeeName;
        String body = buildStatusBody(employeeName, leaveType, leaveDates, reason, status, actionBy, rejectionReason);
        sendInBatches(recipients, subject, body);
    }

    private void sendInBatches(List<String> recipients, String subject, String htmlBody) {
        int size = batchSize > 0 ? batchSize : 25;
        for (int i = 0; i < recipients.size(); i += size) {
            List<String> batch = recipients.subList(i, Math.min(i + size, recipients.size()));
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(batch.toArray(new String[0]));
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(message);
            } catch (Exception e) {
                LOGGER.error("[LeaveEmailService] Failed to send email batch: {}", e.getMessage(), e);
            }
        }
    }

    private String buildPendingApprovalBody(String employeeName, String leaveType,
                                             List<LeaveDate> leaveDates, String reason) {
        return "<html><body>" +
               "<h3>Leave Approval Required</h3>" +
               "<p><b>Employee:</b> " + safe(employeeName) + "</p>" +
               "<p><b>Leave Type:</b> " + safe(leaveType) + "</p>" +
               "<p><b>Dates:</b> " + formatDates(leaveDates) + "</p>" +
               "<p><b>Reason:</b> " + safe(reason) + "</p>" +
               "<p>Please log in to approve or reject this request.</p>" +
               "</body></html>";
    }

    private String buildStatusBody(String employeeName, String leaveType, List<LeaveDate> leaveDates,
                                    String reason, String status, String actionBy, String rejectionReason) {
        StringBuilder sb = new StringBuilder("<html><body>")
                .append("<h3>Leave ").append(capitalize(status)).append("</h3>")
                .append("<p><b>Employee:</b> ").append(safe(employeeName)).append("</p>")
                .append("<p><b>Leave Type:</b> ").append(safe(leaveType)).append("</p>")
                .append("<p><b>Dates:</b> ").append(formatDates(leaveDates)).append("</p>")
                .append("<p><b>Reason:</b> ").append(safe(reason)).append("</p>")
                .append("<p><b>Action By:</b> ").append(safe(actionBy)).append("</p>");
        if ("REJECTED".equalsIgnoreCase(status) && rejectionReason != null && !rejectionReason.isBlank()) {
            sb.append("<p><b>Rejection Reason:</b> ").append(safe(rejectionReason)).append("</p>");
        }
        return sb.append("</body></html>").toString();
    }

    private String formatDates(List<LeaveDate> leaveDates) {
        if (leaveDates == null || leaveDates.isEmpty()) return "N/A";
        return leaveDates.stream()
                .map(d -> d.getLeaveDate().format(DATE_FMT) + " (" + d.getDayType() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("N/A");
    }

    private String safe(String value) { return value != null ? value : ""; }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }
}
