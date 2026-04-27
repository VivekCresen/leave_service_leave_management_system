package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.EmailConfiguration;
import com.cresensolutions.leaveservice.model.EmailTemplate;
import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.model.PublicHoliday;
import com.cresensolutions.leaveservice.repository.EmailConfigurationRepository;
import com.cresensolutions.leaveservice.repository.EmailTemplateRepository;
import com.cresensolutions.leaveservice.repository.LeaveDateRepository;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.cresensolutions.leaveservice.service.HolidayService;
import com.cresensolutions.leaveservice.service.LeaveEmailService;
import com.cresensolutions.leaveservice.service.LeaveReminderDispatchService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

@Service
@Transactional(readOnly = true)
public class HolidayServiceImpl implements HolidayService{

    private final PublicHolidayRepository holidayRepository;

    public HolidayServiceImpl(PublicHolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Override
    public List<HolidayResponse> getHolidays(Integer year) {
        List<PublicHoliday> holidays = year != null
                ? holidayRepository.findByYear(year)
                : holidayRepository.findAllOrderedByDate();
        return holidays.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public HolidayResponse createHoliday(CreateHolidayRequest request) {
        if (holidayRepository.existsByDateAndNameIgnoreCase(request.date(), request.name())) {
            throw new IllegalArgumentException(
                    "A holiday named '" + request.name() + "' already exists on " + request.date());
        }
        PublicHoliday holiday = new PublicHoliday(
                request.name().trim(),
                request.date(),
                request.description() != null ? request.description().trim() : null,
                request.createdBy()
        );
        return toResponse(holidayRepository.save(holiday));
    }

    @Override
    @Transactional
    public HolidayResponse updateHoliday(Long id, CreateHolidayRequest request) {
        PublicHoliday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id: " + id));
        holiday.update(
                request.name().trim(),
                request.date(),
                request.description() != null ? request.description().trim() : null
        );
        return toResponse(holidayRepository.save(holiday));
    }

    @Override
    @Transactional
    public void deleteHoliday(Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Holiday not found with id: " + id);
        }
        holidayRepository.deleteById(id);
    }

    private HolidayResponse toResponse(PublicHoliday h) {
        return new HolidayResponse(h.getId(), h.getName(), h.getDate(),
                h.getDescription(), h.getCreatedBy(), h.getCreatedAt());
    }

    @Slf4j
    @Service
    public static class LeaveEmailServiceImpl implements LeaveEmailService {
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
        private static final String DEFAULT_LOGIN_URL = "http://localhost:4200/login";

        private final EmailConfigurationRepository emailConfigRepo;
        private final EmailTemplateRepository emailTemplateRepo;
        private final String loginUrl;

        public LeaveEmailServiceImpl(
                EmailConfigurationRepository emailConfigRepo,
                EmailTemplateRepository emailTemplateRepo,
                @Value("${app.login-url:http://localhost:4200/login}") String loginUrl
        ) {
            this.emailConfigRepo   = emailConfigRepo;
            this.emailTemplateRepo = emailTemplateRepo;
            this.loginUrl          = (loginUrl == null || loginUrl.isBlank()) ? DEFAULT_LOGIN_URL : loginUrl;
        }

        @Override
        @Async
        public void sendManagerApprovedPendingAdminNotification(
                List<String> adminRecipients, List<String> employeeRecipients,
                String employeeName, String leaveType, List<LeaveDate> leaveDates,
                String reason, String managerName, Long leaveId) {
            EmailConfiguration config = resolveConfig();
            if (config == null) return;
            String datesTable = buildDatesTable(leaveDates);
            String approveUrl = buildMailDecisionUrl(leaveId, LeaveConstants.STATUS_APPROVED, LeaveConstants.ROLE_ADMIN);
            String rejectUrl = buildMailDecisionUrl(leaveId, LeaveConstants.STATUS_REJECTED, LeaveConstants.ROLE_ADMIN);
    
            if (hasRecipients(adminRecipients)) {
                String subject = resolveSubject(LeaveConstants.TMPL_MANAGER_APPROVED_PENDING,
                        "Leave Approved by Manager – Awaiting Your Final Approval");
                String body = resolveBody(LeaveConstants.TMPL_MANAGER_APPROVED_PENDING)
                        .replace("{{employeeName}}", safe(employeeName))
                        .replace("{{leaveType}}", safe(leaveType))
                        .replace("{{reason}}", safe(reason))
                        .replace("{{managerName}}", safe(managerName))
                        .replace("{{datesTable}}", datesTable)
                        .replace("{{loginUrl}}", safe(this.loginUrl))
                        .replace("{{approveUrl}}", safe(approveUrl))
                        .replace("{{rejectUrl}}", safe(rejectUrl));
                sendToAll(config, adminRecipients, subject, "Leave Pending Admin Approval", body);
            }
            if (hasRecipients(employeeRecipients)) {
                String subject = "Your leave request has been approved by your manager";
                String body = "<p style=\"margin:0 0 10px;color:#475569;font-size:14px;line-height:1.6;\">Hello <strong>" + safe(employeeName) + "</strong>,</p>"
                        + "<p style=\"margin:0 0 14px;color:#475569;font-size:14px;line-height:1.6;\">Your leave request has been <strong style=\"color:#0f8b8d;\">approved by your manager</strong> and is now awaiting final approval from the administrator.</p>"
                        + "<div style=\"margin:0 0 16px;padding:12px 16px;border-radius:12px;background:#ecfdf5;border:1px solid #bbf7d0;\">"
                        + "<span style=\"font-size:12px;font-weight:700;color:#0f8b8d;letter-spacing:0.06em;\">✅ MANAGER APPROVED – PENDING ADMIN FINAL APPROVAL</span>"
                        + "</div>"
                        + "<table role=\"presentation\" style=\"width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 14px;\">"
                        + "<tr><td style=\"width:36%;padding:10px 12px;border-radius:10px 0 0 10px;background:#f8fafc;color:#64748b;font-size:12px;font-weight:700;\">Leave Type</td>"
                        + "<td style=\"padding:10px 12px;border-radius:0 10px 10px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:13px;\">" + safe(leaveType) + "</td></tr>"
                        + "<tr><td style=\"padding:10px 12px;border-radius:10px 0 0 10px;background:#f8fafc;color:#64748b;font-size:12px;font-weight:700;\">Approved By</td>"
                        + "<td style=\"padding:10px 12px;border-radius:0 10px 10px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:13px;\"><strong>" + safe(managerName) + "</strong> <span style=\"color:#64748b;font-size:12px;background:#f1f5f9;padding:2px 7px;border-radius:5px;margin-left:4px;\">Manager</span></td></tr>"
                        + "</table>"
                        + datesTable
                        + "<div style=\"padding:12px 14px;border-radius:12px;background:#f0fdfa;border:1px solid #99f6e4;margin-bottom:4px;\">"
                        + "<p style=\"margin:0;color:#0f766e;font-size:13px;line-height:1.6;\">You will receive another notification once the administrator makes a final decision.</p>"
                        + "</div>";
                sendToAll(config, employeeRecipients, subject, "Leave Pending Admin Approval", body);
            }
        }

        public void sendManagerApprovedPendingAdminNotification(
                List<String> adminRecipients, List<String> employeeRecipients,
                String employeeName, String leaveType, List<LeaveDate> leaveDates,
                String reason, String managerName) {
            sendManagerApprovedPendingAdminNotification(adminRecipients, employeeRecipients,
                    employeeName, leaveType, leaveDates, reason, managerName, null);
        }

        @Override
        @Async
        public void sendPendingApprovalReminder(List<String> recipients, String employeeName,
                                                String employeeRole, String leaveType,
                                                List<LeaveDate> leaveDates, String reason,
                                                String managerName, String managerRole, String loginUrl,
                                                Long leaveId) {
            EmailConfiguration config = resolveConfig();
            if (config == null || !hasRecipients(recipients)) return;
            String datesTable = buildDatesTable(leaveDates);
            String effectiveLoginUrl = (loginUrl != null && !loginUrl.isBlank()) ? loginUrl : this.loginUrl;
            String approveUrl = buildMailDecisionUrl(
                    effectiveLoginUrl, leaveId, LeaveConstants.STATUS_APPROVED, "MANAGER_OR_ADMIN");
            String rejectUrl = buildMailDecisionUrl(
                    effectiveLoginUrl, leaveId, LeaveConstants.STATUS_REJECTED, "MANAGER_OR_ADMIN");
            String subject = resolveSubject(LeaveConstants.TMPL_PENDING_APPROVAL, "Leave Approval Required");
            String body = resolveBody(LeaveConstants.TMPL_PENDING_APPROVAL)
                    .replace("{{employeeName}}", safe(employeeName))
                    .replace("{{employeeRole}}", safe(employeeRole))
                    .replace("{{managerName}}", safe(managerName))
                    .replace("{{managerRole}}", safe(managerRole))
                    .replace("{{leaveType}}", safe(leaveType))
                    .replace("{{reason}}", safe(reason))
                    .replace("{{datesTable}}", datesTable)
                    .replace("{{loginUrl}}", safe(effectiveLoginUrl))
                    .replace("{{approveUrl}}", safe(approveUrl))
                    .replace("{{rejectUrl}}", safe(rejectUrl));
            sendToAll(config, recipients, subject, "Leave Approval Required", body);
        }

        public void sendPendingApprovalReminder(List<String> recipients, String employeeName,
                                                String employeeRole, String leaveType,
                                                List<LeaveDate> leaveDates, String reason,
                                                String managerName, String managerRole, String loginUrl) {
            sendPendingApprovalReminder(recipients, employeeName, employeeRole, leaveType,
                    leaveDates, reason, managerName, managerRole, loginUrl, null);
        }

        @Override
        @Async
        public void sendLeaveStatusNotification(List<String> recipients, String employeeName,
                String leaveType, List<LeaveDate> leaveDates, String reason,
                String status, String actionBy, String actionByRole, String rejectionReason) {
            EmailConfiguration config = resolveConfig();
            if (config == null || !hasRecipients(recipients)) return;
            boolean approved = LeaveConstants.STATUS_APPROVED.equalsIgnoreCase(status);
            String templateType = approved ? LeaveConstants.TMPL_LEAVE_APPROVED : LeaveConstants.TMPL_LEAVE_REJECTED;
            String statusLabel  = approved ? "Approved" : "Rejected";
            String datesTable = buildDatesTable(leaveDates);
            String subject = resolveSubject(templateType, "Leave " + statusLabel);
            String body = resolveBody(templateType)
                    .replace("{{employeeName}}", safe(employeeName))
                    .replace("{{leaveType}}", safe(leaveType))
                    .replace("{{reason}}", safe(reason))
                    .replace("{{actionBy}}", safe(actionBy))
                    .replace("{{actionByRole}}", safe(actionByRole))
                    .replace("{{rejectionReason}}", safe(rejectionReason))
                    .replace("{{datesTable}}", datesTable)
                    .replace("{{loginUrl}}", safe(this.loginUrl));
            sendToAll(config, recipients, subject, "Leave " + statusLabel, body);
        }

        @Override
        @Async
        public void sendReminderNotification(List<String> recipients, String employeeName,
                String leaveType, List<LeaveDate> leaveDates, String reason, String reminderType) {
            EmailConfiguration config = resolveConfig();
            if (config == null || !hasRecipients(recipients)) return;
            String templateType = LeaveConstants.REMINDER_4DAY.equals(reminderType)
                    ? LeaveConstants.TMPL_REMINDER_4DAY
                    : LeaveConstants.TMPL_REMINDER_2DAY;
            String days = LeaveConstants.REMINDER_4DAY.equals(reminderType) ? "4" : "2";
            String datesTable = buildDatesTable(leaveDates);
            String subject = resolveSubject(templateType,
                    "Reminder: Leave Approval Needed in " + days + " Days");
            String body = resolveBody(templateType)
                    .replace("{{employeeName}}", safe(employeeName))
                    .replace("{{leaveType}}", safe(leaveType))
                    .replace("{{reason}}", safe(reason))
                    .replace("{{datesTable}}", datesTable);
            sendToAll(config, recipients, subject, "Leave Approval Reminder", body);
        }

        @Override
        @Async
        public void sendPartialLeaveStatusNotification(List<String> recipients, String employeeName,
                String leaveType, List<LeaveDate> approvedDates, List<LeaveDate> rejectedDates,
                String reason, String actionBy, String actionByRole, String rejectionReason) {
            EmailConfiguration config = resolveConfig();
            if (config == null || !hasRecipients(recipients)) return;

            boolean hasApproved = approvedDates != null && !approvedDates.isEmpty();
            boolean hasRejected = rejectedDates != null && !rejectedDates.isEmpty();

            // All approved → single approval email
            if (hasApproved && !hasRejected) {
                sendLeaveStatusNotification(recipients, employeeName, leaveType, approvedDates,
                        reason, LeaveConstants.STATUS_APPROVED, actionBy, actionByRole, null);
                return;
            }
            // All rejected → single rejection email
            if (!hasApproved && hasRejected) {
                sendLeaveStatusNotification(recipients, employeeName, leaveType, rejectedDates,
                        reason, LeaveConstants.STATUS_REJECTED, actionBy, actionByRole, rejectionReason);
                return;
            }
            // Mixed → two separate emails
            if (hasApproved) {
                String approvedTable = buildDatesTable(approvedDates);
                String approvedSubject = resolveSubject(LeaveConstants.TMPL_LEAVE_APPROVED, "Some Leave Dates Approved ✅");
                String approvedBody = resolveBody(LeaveConstants.TMPL_LEAVE_APPROVED)
                        .replace("{{employeeName}}", safe(employeeName))
                        .replace("{{leaveType}}", safe(leaveType))
                        .replace("{{reason}}", safe(reason))
                        .replace("{{actionBy}}", safe(actionBy))
                        .replace("{{actionByRole}}", safe(actionByRole))
                        .replace("{{rejectionReason}}", "")
                        .replace("{{datesTable}}", approvedTable);
                sendToAll(config, recipients, approvedSubject, "Leave Dates Approved", approvedBody);
            }
            if (hasRejected) {
                String rejectedTable = buildDatesTable(rejectedDates);
                String rejectedSubject = resolveSubject(LeaveConstants.TMPL_LEAVE_REJECTED, "Some Leave Dates Rejected");
                String rejectedBody = resolveBody(LeaveConstants.TMPL_LEAVE_REJECTED)
                        .replace("{{employeeName}}", safe(employeeName))
                        .replace("{{leaveType}}", safe(leaveType))
                        .replace("{{reason}}", safe(reason))
                        .replace("{{actionBy}}", safe(actionBy))
                        .replace("{{actionByRole}}", safe(actionByRole))
                        .replace("{{rejectionReason}}", safe(rejectionReason))
                        .replace("{{datesTable}}", rejectedTable);
                sendToAll(config, recipients, rejectedSubject, "Leave Dates Rejected", rejectedBody);
            }
        }

        private EmailConfiguration resolveConfig() {
            Optional<EmailConfiguration> opt = emailConfigRepo.findFirstByActiveTrueOrderByIdAsc();
            if (opt.isEmpty()) {
                log.warn("[LeaveEmailService] No active email configuration in DB, skipping.");
                return null;
            }
            return opt.get();
        }

        private String resolveSubject(String templateType, String fallback) {
            return emailTemplateRepo.findByTemplateTypeAndActiveTrue(templateType)
                    .map(EmailTemplate::getSubject)
                    .orElse(fallback);
        }

        private String resolveBody(String templateType) {
            return emailTemplateRepo.findByTemplateTypeAndActiveTrue(templateType)
                    .map(EmailTemplate::getBodyHtml)
                    .orElseGet(() -> {
                        log.warn("[LeaveEmailService] No active template for type={}", templateType);
                        return "";
                    });
        }

        private String buildMailDecisionUrl(Long leaveId, String decision, String expectedRole) {
            return buildMailDecisionUrl(this.loginUrl, leaveId, decision, expectedRole);
        }

        private String buildMailDecisionUrl(String baseUrl, Long leaveId, String decision, String expectedRole) {
            String effectiveBaseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_LOGIN_URL : baseUrl;
            if (leaveId == null) {
                return effectiveBaseUrl;
            }
            String separator = effectiveBaseUrl.contains("?") ? "&" : "?";
            return effectiveBaseUrl + separator
                    + "mailLeaveId=" + URLEncoder.encode(String.valueOf(leaveId), StandardCharsets.UTF_8)
                    + "&mailDecision=" + URLEncoder.encode(decision, StandardCharsets.UTF_8)
                    + "&mailRole=" + URLEncoder.encode(expectedRole, StandardCharsets.UTF_8);
        }

        private void sendToAll(EmailConfiguration config, List<String> recipients,
                String subject, String headerTitle, String bodyHtml) {
            try {
                if (isDryRunMailConfig(config)) {
                    log.info("[LeaveEmailService] Dry-run skip for '{}' to {} recipient(s)", subject, recipients.size());
                    return;
                }
                JavaMailSender sender = buildSender(config);
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(config.getFromAddress());
                helper.setTo(recipients.toArray(new String[0]));
                helper.setSubject(subject);
                helper.setText(wrapInLayout(headerTitle, bodyHtml, config.getLogoPath()), true);
                addLogoIfAvailable(helper, config.getLogoPath());
                sender.send(message);
                log.info("[LeaveEmailService] Sent '{}' to {} recipient(s)", subject, recipients.size());
            } catch (MessagingException | MailException e) {
                log.error("[LeaveEmailService] Failed to send '{}': {}", subject, e.getMessage(), e);
            }
        }

        private boolean isDryRunMailConfig(EmailConfiguration config) {
            String host = safe(config.getHost()).trim();
            String fromAddress = safe(config.getFromAddress()).trim().toLowerCase(Locale.ROOT);
            return ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))
                    && fromAddress.endsWith(".local");
        }

        private JavaMailSender buildSender(EmailConfiguration config) {
            String protocol = config.getProtocol() == null || config.getProtocol().isBlank()
                    ? "smtp" : config.getProtocol();
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(config.getHost());
            sender.setPort(config.getPort() == null ? 25 : config.getPort());
            sender.setUsername(config.getUsername());
            sender.setPassword(config.getPassword());
            sender.setProtocol(protocol);
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", protocol);
            props.put("mail.smtp.auth", Boolean.toString(config.isAuth()));
            props.put("mail.smtp.starttls.enable", Boolean.toString(config.isStarttlsEnabled()));
            props.put("mail.smtp.ssl.enable", Boolean.toString(config.isSslEnabled()));
            return sender;
        }

        private String wrapInLayout(String headerTitle, String contentHtml, String logoPath) {
            return """
                    <!doctype html>
                    <html>
                      <body style="margin:0;padding:16px;background:#eef4f8;font-family:Arial,'Helvetica Neue',sans-serif;">
                        <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #dbe4ee;border-radius:20px;overflow:hidden;box-shadow:0 16px 40px -30px rgba(15,23,42,0.35);">
                          <div style="padding:20px 24px;background:linear-gradient(135deg,#f0fdfa,#fff7ed);border-bottom:1px solid #e2e8f0;">
                            %s
                            <div style="margin-top:12px;font-size:11px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;">Cresen Solutions</div>
                            <h1 style="margin:6px 0 0;color:#0f172a;font-size:22px;line-height:1.2;">%s</h1>
                          </div>
                          <div style="padding:20px 24px 22px;">%s</div>
                          <div style="padding:14px 24px;background:#f8fafc;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px;line-height:1.7;">
                            Cresen Solutions LLC<br>This is an automated email from the Leave Management System.
                          </div>
                        </div>
                      </body>
                    </html>
                    """.formatted(resolveLogoMarkup(logoPath), headerTitle, contentHtml);
        }

        private String buildDatesTable(List<LeaveDate> leaveDates) {
            if (leaveDates == null || leaveDates.isEmpty()) return "";
            StringBuilder rows = new StringBuilder();
            for (LeaveDate d : leaveDates) {
                String dateStr = d.getLeaveDate() != null ? d.getLeaveDate().format(DATE_FMT) : "—";
                String session = formatSession(d.getDayType());
                String color   = sessionColor(d.getDayType());
                rows.append("""
                        <tr>
                          <td style="padding:10px 14px;border-bottom:1px solid #f1f5f9;color:#0f172a;font-size:14px;">%s</td>
                          <td style="padding:10px 14px;border-bottom:1px solid #f1f5f9;">
                            <span style="font-size:12px;font-weight:700;color:%s;background:%s18;padding:2px 8px;border-radius:6px;">%s</span>
                          </td>
                        </tr>
                        """.formatted(dateStr, color, color, session));
            }
            double total = leaveDates.stream()
                    .mapToDouble(d -> d.getDayType() != null
                            && d.getDayType().contains(LeaveConstants.DAY_TYPE_HALF_KEYWORD) ? 0.5 : 1.0)
                    .sum();
            String totalStr = total == Math.floor(total) ? String.valueOf((int) total) : String.valueOf(total);
            return """
                    <div style="margin:0 0 18px;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;">
                      <table role="presentation" style="width:100%%;border-collapse:collapse;">
                        <thead><tr style="background:#f8fafc;">
                          <th style="padding:10px 14px;text-align:left;font-size:12px;font-weight:700;color:#64748b;letter-spacing:0.08em;text-transform:uppercase;">Date</th>
                          <th style="padding:10px 14px;text-align:left;font-size:12px;font-weight:700;color:#64748b;letter-spacing:0.08em;text-transform:uppercase;">Session</th>
                        </tr></thead>
                        <tbody>%s</tbody>
                        <tfoot><tr style="background:#f8fafc;">
                          <td colspan="2" style="padding:10px 14px;font-size:13px;font-weight:700;color:#0f766e;text-align:right;">Total: %s day%s</td>
                        </tr></tfoot>
                      </table>
                    </div>
                    """.formatted(rows, totalStr, total == 1.0 ? "" : "s");
        }

        private String formatSession(String dayType) {
            if (dayType == null) return "Full Day";
            return switch (dayType.toUpperCase()) {
                case LeaveConstants.DAY_TYPE_MORNING_HALF   -> "Morning Half";
                case LeaveConstants.DAY_TYPE_AFTERNOON_HALF -> "Afternoon Half";
                default                                     -> "Full Day";
            };
        }

        private String sessionColor(String dayType) {
            if (dayType == null) return "#0f766e";
            return switch (dayType.toUpperCase()) {
                case LeaveConstants.DAY_TYPE_MORNING_HALF   -> "#0369a1";
                case LeaveConstants.DAY_TYPE_AFTERNOON_HALF -> "#7c3aed";
                default                                     -> "#0f766e";
            };
        }

        private String resolveLogoMarkup(String logoPath) {
            if (resolveLogoFile(logoPath) == null) return "";
            return "<img src=\"cid:" + LeaveConstants.LOGO_CID + "\" alt=\"Cresen Solutions Logo\" style=\"display:block;height:56px;width:auto;\">";
        }

        private void addLogoIfAvailable(MimeMessageHelper helper, String logoPath) throws MessagingException {
            File f = resolveLogoFile(logoPath);
            if (f != null) helper.addInline(LeaveConstants.LOGO_CID, new FileSystemResource(f), "image/png");
        }

        private File resolveLogoFile(String logoPath) {
            if (logoPath == null || logoPath.isBlank()) return null;
            File f = new File(logoPath);
            if (!f.exists() || !f.isFile()) {
                log.warn("[LeaveEmailService] Logo not found at {}", logoPath);
                return null;
            }
            return f;
        }

        private boolean hasRecipients(List<String> recipients) {
            if (recipients == null || recipients.isEmpty()) {
                log.warn("[LeaveEmailService] No recipients, skipping email.");
                return false;
            }
            return true;
        }

        private String safe(String v) { return v != null ? v : ""; }
    }

    @Slf4j
    @Service
    public static class LeaveReminderDispatchServiceImpl implements LeaveReminderDispatchService {

        private final LeaveEmailService leaveEmailService;
        private final LeaveDateRepository leaveDateRepository;

        public LeaveReminderDispatchServiceImpl(
                LeaveEmailService leaveEmailService,
                LeaveDateRepository leaveDateRepository
        ) {
            this.leaveEmailService = leaveEmailService;
            this.leaveDateRepository = leaveDateRepository;
        }

        @Override
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
                log.warn("[LeaveReminderDispatchService] No recipients for {} reminder, leaveId={}", reminderType, leaveId);
                return false;
            }

            List<LeaveDate> dates = leaveId != null
                    ? leaveDateRepository.findByApplicationId(leaveId)
                    : List.of();

            log.info("[LeaveReminderDispatchService] Dispatching {} reminder for leaveId={} to {} recipient(s)",
                    reminderType, leaveId, recipients.size());

            leaveEmailService.sendReminderNotification(
                    recipients,
                    employeeName != null ? employeeName : LeaveConstants.DEFAULT_EMPLOYEE_NAME,
                    leaveType != null ? leaveType : "",
                    dates,
                    reason != null ? reason : "",
                    reminderType
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
}
