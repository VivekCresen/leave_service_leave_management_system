package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveEmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private LeaveEmailService leaveEmailService;

    private LeaveDate leaveDate;

    @BeforeEach
    void setUp() {
        LeaveType leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        LeaveRecord leave = new LeaveRecord(null, leaveType, "Vacation", null, null, true);
        leaveDate = new LeaveDate(leave, LocalDate.of(2026, 4, 10), "FULL");
    }

    @Test
    void sendPendingApprovalReminder_noFromAddress_skipsEmail() {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "");

        leaveEmailService.sendPendingApprovalReminder(
                List.of("manager@example.com"), "John", "Annual Leave",
                List.of(leaveDate), "Vacation");

        verify(mailSender, never()).send(any(MimeMessage[].class));
    }

    @Test
    void sendPendingApprovalReminder_noRecipients_skipsEmail() {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");

        leaveEmailService.sendPendingApprovalReminder(
                List.of(), "John", "Annual Leave", List.of(leaveDate), "Vacation");

        verify(mailSender, never()).send(any(MimeMessage[].class));
    }

    @Test
    void sendPendingApprovalReminder_nullRecipients_skipsEmail() {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");

        leaveEmailService.sendPendingApprovalReminder(
                null, "John", "Annual Leave", List.of(leaveDate), "Vacation");

        verify(mailSender, never()).send(any(MimeMessage[].class));
    }

    @Test
    void sendLeaveStatusNotification_noFromAddress_skipsEmail() {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "");

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@example.com"), "John", "Annual Leave",
                List.of(leaveDate), "Vacation", "APPROVED", "manager1", null);

        verify(mailSender, never()).send(any(MimeMessage[].class));
    }

    @Test
    void sendLeaveStatusNotification_noRecipients_skipsEmail() {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");

        leaveEmailService.sendLeaveStatusNotification(
                List.of(), "John", "Annual Leave",
                List.of(leaveDate), "Vacation", "APPROVED", "manager1", null);

        verify(mailSender, never()).send(any(MimeMessage[].class));
    }

    @Test
    void sendLeaveStatusNotification_withValidRecipients_sendsMail() throws Exception {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(leaveEmailService, "batchSize", 25);
        ReflectionTestUtils.setField(leaveEmailService, "logoPath", "");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@example.com"), "John", "Annual Leave",
                List.of(leaveDate), "Vacation", "APPROVED", "manager1", null);

        verify(mailSender, atLeastOnce()).createMimeMessage();
    }

    @Test
    void sendPendingApprovalReminder_withValidRecipients_sendsMail() throws Exception {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(leaveEmailService, "batchSize", 25);
        ReflectionTestUtils.setField(leaveEmailService, "logoPath", "");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        leaveEmailService.sendPendingApprovalReminder(
                List.of("manager@example.com"), "John", "Annual Leave",
                List.of(leaveDate), "Vacation");

        verify(mailSender, atLeastOnce()).createMimeMessage();
    }

    @Test
    void sendLeaveStatusNotification_rejectedWithReason_sendsMail() throws Exception {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(leaveEmailService, "batchSize", 25);
        ReflectionTestUtils.setField(leaveEmailService, "logoPath", "");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@example.com"), "John", "Annual Leave",
                List.of(leaveDate), "Vacation", "REJECTED", "manager1", "Not enough notice");

        verify(mailSender, atLeastOnce()).createMimeMessage();
    }

    @Test
    void sendLeaveStatusNotification_emptyLeaveDates_doesNotThrow() throws Exception {
        ReflectionTestUtils.setField(leaveEmailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(leaveEmailService, "batchSize", 25);
        ReflectionTestUtils.setField(leaveEmailService, "logoPath", "");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@example.com"), "John", "Annual Leave",
                List.of(), "Vacation", "APPROVED", "manager1", null);

        verify(mailSender, atLeastOnce()).createMimeMessage();
    }
}
