package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.repository.LeaveDateRepository;
import com.cresensolutions.leaveservice.service.Impl.HolidayServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveReminderDispatchServiceImplTest {

    @Mock private LeaveEmailService leaveEmailService;
    @Mock private LeaveDateRepository leaveDateRepository;
    @InjectMocks private HolidayServiceImpl.LeaveReminderDispatchServiceImpl service;

    @Test
    void dispatchReminder_withManagerAndAdmin_sendsToTwo() {
        LeaveType lt = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        LeaveRecord leave = new LeaveRecord(null, lt, "Vacation", null, null, true);
        LeaveDate ld = new LeaveDate(leave, LocalDate.now(), "FULL");

        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(ld));

        boolean result = service.dispatchReminder(
                10L, "4DAY", "manager@cresensolutions.com", "admin@cresensolutions.com",
                "John Doe", "Annual Leave", "Vacation", "proc-1", "task-1");

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                argThat(r -> r.size() == 2 && r.contains("manager@cresensolutions.com") && r.contains("admin@cresensolutions.com")),
                eq("John Doe"), eq("Annual Leave"), eq(List.of(ld)), eq("Vacation"), eq("4DAY"));
    }

    @Test
    void dispatchReminder_sameManagerAndAdmin_sendsToOne() {
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        boolean result = service.dispatchReminder(
                10L, "2DAY", "same@cresensolutions.com", "same@cresensolutions.com",
                "John", "Annual Leave", "Vacation", null, null);

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                argThat(r -> r.size() == 1),
                any(), any(), any(), any(), any());
    }

    @Test
    void dispatchReminder_noRecipients_returnsFalse() {
        boolean result = service.dispatchReminder(
                10L, "4DAY", null, null,
                "John", "Annual Leave", "Vacation", null, null);

        assertThat(result).isFalse();
        verifyNoInteractions(leaveEmailService);
        verifyNoInteractions(leaveDateRepository);
    }

    @Test
    void dispatchReminder_blankManagerAndAdmin_returnsFalse() {
        boolean result = service.dispatchReminder(
                10L, "4DAY", "  ", "",
                "John", "Annual Leave", "Vacation", null, null);

        assertThat(result).isFalse();
        verifyNoInteractions(leaveEmailService);
    }

    @Test
    void dispatchReminder_nullLeaveId_usesEmptyDates() {
        boolean result = service.dispatchReminder(
                null, "4DAY", "manager@cresensolutions.com", null,
                "John", "Annual Leave", "Vacation", null, null);

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                any(), eq("John"), eq("Annual Leave"), eq(List.of()), eq("Vacation"), eq("4DAY"));
        verifyNoInteractions(leaveDateRepository);
    }

    @Test
    void dispatchReminder_nullEmployeeName_usesDefault() {
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        boolean result = service.dispatchReminder(
                10L, "2DAY", "manager@cresensolutions.com", null,
                null, "Annual Leave", "Vacation", null, null);

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                any(), eq("Employee"), any(), any(), any(), any());
    }

    @Test
    void dispatchReminder_nullLeaveType_usesEmpty() {
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        boolean result = service.dispatchReminder(
                10L, "2DAY", "manager@cresensolutions.com", null,
                "John", null, "Vacation", null, null);

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                any(), any(), eq(""), any(), any(), any());
    }

    @Test
    void dispatchReminder_nullReason_usesEmpty() {
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        boolean result = service.dispatchReminder(
                10L, "2DAY", "manager@cresensolutions.com", null,
                "John", "Annual Leave", null, null, null);

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                any(), any(), any(), any(), eq(""), any());
    }

    @Test
    void dispatchReminder_onlyAdminEmail_sendsToAdmin() {
        when(leaveDateRepository.findByApplicationId(5L)).thenReturn(List.of());

        boolean result = service.dispatchReminder(
                5L, "4DAY", null, "admin@cresensolutions.com",
                "Jane", "Sick Leave", "Sick", null, null);

        assertThat(result).isTrue();
        verify(leaveEmailService).sendReminderNotification(
                argThat(r -> r.size() == 1 && r.contains("admin@cresensolutions.com")),
                any(), any(), any(), any(), any());
    }
}
