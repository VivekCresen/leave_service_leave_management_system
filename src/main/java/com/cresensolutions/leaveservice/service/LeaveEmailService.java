package com.cresensolutions.leaveservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LeaveEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveEmailService.class);
    private static final String LOGO_CID = "cresenSolutionsLogo";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String fromAddress;

    @Value("${app.mail.logo-path:}")
    private String logoPath;

    public LeaveEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("leaveTaskExecutor")
    public void sendLeaveApprovedNotification(
            List<String> notifyEmails,
            String employeeName,
            String leaveType,
            LocalDate fromDate,
            LocalDate toDate,
            boolean halfDay,
            String halfDaySession,
            String reason
    ) {
        if (fromAddress == null || fromAddress.isBlank()) {
            LOGGER.warn("[LeaveEmail] Mail sender not configured — skipping leave notification emails.");
            return;
        }

        if (notifyEmails == null || notifyEmails.isEmpty()) {
            return;
        }

        String dateRange = halfDay
                ? DATE_FMT.format(fromDate) + " (" + formatSession(halfDaySession) + ")"
                : DATE_FMT.format(fromDate) + " – " + DATE_FMT.format(toDate);

        String durationLabel = halfDay ? "Half day" : "Full day(s)";

        String content = """
                <p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">
                  Hi there,
                </p>
                <p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">
                  This is an automated notification to let you know that a team member's leave has been <strong style="color:#0f766e;">approved</strong>.
                </p>
                <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
                  <tr>
                    <td style="width:36%%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
                    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;font-weight:600;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave type</td>
                    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Date</td>
                    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Duration</td>
                    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
                    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                </table>
                <div style="padding:16px 18px;border-radius:16px;background:rgba(15,139,141,0.06);border:1px solid rgba(15,139,141,0.18);">
                  <p style="margin:0;color:#0f766e;font-size:14px;line-height:1.7;font-weight:600;">
                    📅 %s will be on leave during the above period. Please plan accordingly.
                  </p>
                </div>
                """.formatted(
                employeeName,
                leaveType,
                dateRange,
                durationLabel,
                reason != null && !reason.isBlank() ? reason : "—",
                employeeName
        );

        for (String email : notifyEmails) {
            sendHtmlEmail(
                    email,
                    employeeName + " is on leave — Cresen Solutions",
                    "Leave Approved Notification",
                    content
            );
        }
    }

    private String formatSession(String session) {
        if ("MORNING".equalsIgnoreCase(session)) return "Morning session";
        if ("AFTERNOON".equalsIgnoreCase(session)) return "Afternoon session";
        return session != null ? session : "";
    }

    private void sendHtmlEmail(String to, String subject, String headerTitle, String contentHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildHtml(headerTitle, contentHtml), true);
            addLogoIfAvailable(helper);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            LOGGER.error("[LeaveEmail] Failed to send notification to {}: {}", to, e.getMessage());
        }
    }

    private String buildHtml(String headerTitle, String contentHtml) {
        String logo = resolveLogoMarkup();
        return """
                <!doctype html>
                <html>
                  <body style="margin:0;padding:24px;background:#eef4f8;font-family:Arial,'Helvetica Neue',sans-serif;">
                    <div style="max-width:680px;margin:0 auto;background:#ffffff;border:1px solid #dbe4ee;border-radius:28px;overflow:hidden;box-shadow:0 24px 60px -40px rgba(15,23,42,0.45);">
                      <div style="padding:28px 32px;background:linear-gradient(135deg,#f0fdfa,#fff7ed);border-bottom:1px solid #e2e8f0;">
                        %s
                        <div style="margin-top:16px;font-size:12px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;">Cresen Solutions</div>
                        <h1 style="margin:8px 0 0;color:#0f172a;font-size:26px;line-height:1.2;">%s</h1>
                      </div>
                      <div style="padding:28px 32px 30px;">%s</div>
                      <div style="padding:18px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px;line-height:1.7;">
                        Cresen Solutions LLC &nbsp;·&nbsp; This is an automated email from the Leave Management System.
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(logo, headerTitle, contentHtml);
    }

    private String resolveLogoMarkup() {
        if (logoPath == null || logoPath.isBlank()) return "";
        File f = new File(logoPath);
        if (!f.exists()) return "";
        return """
                <img src="cid:%s" alt="Cresen Solutions" style="display:block;height:52px;width:auto;">
                """.formatted(LOGO_CID);
    }

    private void addLogoIfAvailable(MimeMessageHelper helper) throws MessagingException {
        if (logoPath == null || logoPath.isBlank()) return;
        File f = new File(logoPath);
        if (!f.exists()) return;
        helper.addInline(LOGO_CID, new FileSystemResource(f), "image/png");
    }
}
