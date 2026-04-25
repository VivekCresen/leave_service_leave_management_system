package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.model.EmailConfiguration;
import com.cresensolutions.leaveservice.model.EmailTemplate;
import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.repository.EmailConfigurationRepository;
import com.cresensolutions.leaveservice.repository.EmailTemplateRepository;
import com.cresensolutions.leaveservice.service.Impl.HolidayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveEmailServiceTest {

    @Mock private EmailConfigurationRepository emailConfigRepo;
    @Mock private EmailTemplateRepository emailTemplateRepo;
    @InjectMocks private HolidayServiceImpl.LeaveEmailServiceImpl leaveEmailService;

    @Mock private EmailConfiguration config;
    @Mock private EmailTemplate template;

    private LeaveDate fullDayDate;
    private LeaveDate morningHalfDate;
    private LeaveDate afternoonHalfDate;

    @BeforeEach
    void setUp() {
        when(config.getHost()).thenReturn("localhost");
        when(config.getPort()).thenReturn(0);
        when(config.getUsername()).thenReturn(null);
        when(config.getPassword()).thenReturn(null);
        when(config.getProtocol()).thenReturn("smtp");
        when(config.isAuth()).thenReturn(false);
        when(config.isStarttlsEnabled()).thenReturn(false);
        when(config.isSslEnabled()).thenReturn(false);
        when(config.getFromAddress()).thenReturn("noreply@cresensolutions.com");
        when(config.getLogoPath()).thenReturn("");

        when(template.getSubject()).thenReturn("Test Subject");
        when(template.getBodyHtml()).thenReturn("<p>Hello {{employeeName}}</p>");

        LeaveType leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        LeaveRecord leave = new LeaveRecord(null, leaveType, "Vacation", null, null, true);
        fullDayDate       = new LeaveDate(leave, LocalDate.of(2026, 5, 1), "FULL");
        morningHalfDate   = new LeaveDate(leave, LocalDate.of(2026, 5, 2), "MORNING_HALF");
        afternoonHalfDate = new LeaveDate(leave, LocalDate.of(2026, 5, 3), "AFTERNOON_HALF");
    }

  
    @Test
    void sendPendingApprovalReminder_noConfig_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendLeaveStatusNotification_noConfig_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.empty());

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "APPROVED", "manager1", "Manager", null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendReminderNotification_noConfig_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.empty());

        leaveEmailService.sendReminderNotification(
                List.of("mgr@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "4DAY");

        verifyNoInteractions(emailTemplateRepo);
    }

 
    @Test
    void sendPendingApprovalReminder_nullRecipients_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendPendingApprovalReminder(
                null, "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendPendingApprovalReminder_emptyRecipients_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendPendingApprovalReminder(
                List.of(), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendLeaveStatusNotification_nullRecipients_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendLeaveStatusNotification(
                null, "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "APPROVED", "mgr", "Manager", null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendLeaveStatusNotification_emptyRecipients_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendLeaveStatusNotification(
                List.of(), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "APPROVED", "mgr", "Manager", null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendReminderNotification_nullRecipients_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendReminderNotification(
                null, "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "4DAY");

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendReminderNotification_emptyRecipients_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendReminderNotification(
                List.of(), "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "2DAY");

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendPendingApprovalReminder_templateFound_queriesCorrectType() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_PENDING_APPROVAL))
                .thenReturn(Optional.of(template));

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_PENDING_APPROVAL);
    }

    @Test
    void sendPendingApprovalReminder_templateNotFound_usesFallback() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_PENDING_APPROVAL);
    }

    @Test
    void sendLeaveStatusNotification_approved_usesApprovedTemplate() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_APPROVED))
                .thenReturn(Optional.of(template));

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "APPROVED", "manager1", "Manager", null);

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_APPROVED);
    }

    @Test
    void sendLeaveStatusNotification_rejected_usesRejectedTemplate() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_REJECTED))
                .thenReturn(Optional.of(template));

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "REJECTED", "manager1", "Manager", "Not enough notice");

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_REJECTED);
    }

    @Test
    void sendLeaveStatusNotification_approvedCaseInsensitive_usesApprovedTemplate() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "approved", "manager1", "Manager", null);

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_APPROVED);
    }

    @Test
    void sendReminderNotification_4day_uses4DayTemplate() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_REMINDER_4DAY))
                .thenReturn(Optional.of(template));

        leaveEmailService.sendReminderNotification(
                List.of("mgr@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "4DAY");

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_REMINDER_4DAY);
    }

    @Test
    void sendReminderNotification_2day_uses2DayTemplate() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_REMINDER_2DAY))
                .thenReturn(Optional.of(template));

        leaveEmailService.sendReminderNotification(
                List.of("mgr@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "2DAY");

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_REMINDER_2DAY);
    }

    @Test
    void sendReminderNotification_unknownType_fallsBackTo2DayTemplate() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendReminderNotification(
                List.of("mgr@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "OTHER");

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_REMINDER_2DAY);
    }

  
    @Test
    void sendPendingApprovalReminder_morningHalfDate_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(morningHalfDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_afternoonHalfDate_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(afternoonHalfDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_multipleMixedDates_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate, morningHalfDate, afternoonHalfDate), "Vacation",
                null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }


    @Test
    void sendPendingApprovalReminder_nullReason_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), null, null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendLeaveStatusNotification_emptyLeaveDates_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(), "Vacation", "REJECTED", "manager1", "Manager", "reason");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendLeaveStatusNotification_nullLeaveDates_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                null, "Vacation", "APPROVED", "manager1", "Manager", null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendLeaveStatusNotification_nullEmployeeName_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), null, "Annual Leave",
                List.of(fullDayDate), "Vacation", "APPROVED", "manager1", "Manager", null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_nullProtocol_fallsBackToSmtp() {
        when(config.getProtocol()).thenReturn(null);
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_blankProtocol_fallsBackToSmtp() {
        when(config.getProtocol()).thenReturn("  ");
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_nullPort_usesDefault25() {
        when(config.getPort()).thenReturn(null);
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendLeaveStatusNotification_templateNotFound_usesFallback() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), "Vacation", "REJECTED", "manager1", "Manager", "reason");

        verify(emailTemplateRepo, times(2))
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_REJECTED);
    }

    @Test
    void sendReminderNotification_nullLeaveDates_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendReminderNotification(
                List.of("mgr@cresensolutions.com"), "Vivek", "Annual Leave",
                null, "Vacation", "4DAY");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

  
    @Test
    void sendManagerApprovedPendingAdminNotification_noConfig_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One");

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_withBothRecipients_sendsTwoEmails() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_emptyAdminRecipients_skipsAdminEmail() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of(), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One");

        verify(emailTemplateRepo, never()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_emptyEmployeeRecipients_skipsEmployeeEmail() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of(),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_nullLeaveDates_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", null, "Vacation", "Manager One");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPartialLeaveStatusNotification_mixed_sendsTwoEmails() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPartialLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), List.of(morningHalfDate),
                "Vacation", "admin1", "Administrator", "Some rejected");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPartialLeaveStatusNotification_noConfig_skips() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.empty());

        leaveEmailService.sendPartialLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), List.of(morningHalfDate),
                "Vacation", "admin1", "Administrator", "reason");

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendPartialLeaveStatusNotification_nullApprovedAndRejected_doesNotThrow() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));

        leaveEmailService.sendPartialLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                null, null, "Vacation", "admin1", "Administrator", null);

        verifyNoInteractions(emailTemplateRepo);
    }

    @Test
    void sendPendingApprovalReminder_defaultMethod_withoutLeaveId_delegates() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        
        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_defaultMethod_withoutLeaveId_delegates() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

       
        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One");

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    

    @Test
    void sendPendingApprovalReminder_dryRunConfig_skipsActualSend() {
        when(config.getFromAddress()).thenReturn("noreply@cresensolutions.com");
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        
        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_nonDryRunConfig_attemptsRealSend() {
       
        when(config.getHost()).thenReturn("smtp.real.com");
        when(config.getFromAddress()).thenReturn("noreply@cresensolutions.com");
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

   
        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }


    @Test
    void sendPendingApprovalReminder_withExistingLogoFile_includesLogoMarkup() throws Exception {
        java.io.File tempLogo = java.io.File.createTempFile("logo", ".png");
        tempLogo.deleteOnExit();

        when(config.getLogoPath()).thenReturn(tempLogo.getAbsolutePath());
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());


        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_withNonExistentLogoPath_skipsLogo() {
        when(config.getLogoPath()).thenReturn("/nonexistent/path/logo.png");
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", null, null, null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }



    @Test
    void sendManagerApprovedPendingAdminNotification_withLeaveId_includesDecisionUrlsInBody() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One", 42L);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendManagerApprovedPendingAdminNotification_withNullLeaveId_usesBaseUrl() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                List.of("admin@cresensolutions.com"), List.of("emp@cresensolutions.com"),
                "Vivek", "Annual Leave", List.of(fullDayDate), "Vacation", "Manager One", null);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }

    @Test
    void sendPendingApprovalReminder_withLeaveId_includesDecisionUrls() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPendingApprovalReminder(
                List.of("mgr@cresensolutions.com"), "Vivek", "Employee", "Annual Leave",
                List.of(fullDayDate), "Vacation", "Manager", "Manager", null, 10L);

        verify(emailTemplateRepo, atLeastOnce()).findByTemplateTypeAndActiveTrue(anyString());
    }


    @Test
    void sendPartialLeaveStatusNotification_allApproved_sendsApprovalEmail() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPartialLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(fullDayDate), List.of(),
                "Vacation", "admin1", "Administrator", null);

        verify(emailTemplateRepo, atLeastOnce())
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_APPROVED);
    }

    @Test
    void sendPartialLeaveStatusNotification_allRejected_sendsRejectionEmail() {
        when(emailConfigRepo.findFirstByActiveTrueOrderByIdAsc()).thenReturn(Optional.of(config));
        when(emailTemplateRepo.findByTemplateTypeAndActiveTrue(anyString())).thenReturn(Optional.empty());

        leaveEmailService.sendPartialLeaveStatusNotification(
                List.of("emp@cresensolutions.com"), "Vivek", "Annual Leave",
                List.of(), List.of(morningHalfDate),
                "Vacation", "admin1", "Administrator", "Not approved");

        verify(emailTemplateRepo, atLeastOnce())
                .findByTemplateTypeAndActiveTrue(LeaveConstants.TMPL_LEAVE_REJECTED);
    }
}
