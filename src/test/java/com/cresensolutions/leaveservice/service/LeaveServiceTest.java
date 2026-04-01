package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks
    private LeaveServiceImpl leaveService;

    @Test
    void shouldCreateLeaveUsingUserAndLeaveTypeForeignKeys() {
        UserProfile user = new UserProfileTestBuilder()
                .id(10L)
                .fullName("Vivek Chavda")
                .emailId("vivek@cresen.com")
                .active(true)
                .build();
        LeaveType leaveType = new LeaveType(3, "Casual Leave", "CASUAL");

        when(userProfileRepository.findById(10L)).thenReturn(Optional.of(user));
        when(leaveTypeRepository.findById(3)).thenReturn(Optional.of(leaveType));
        when(leaveRepository.save(any(LeaveRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveResponse response = leaveService.createLeave(new CreateLeaveRequest(
                10L,
                3,
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 4),
                "Family event",
                "Need 3 days",
                "{\"status\":\"submitted\"}",
                true
        ));

        assertEquals(10L, response.userId());
        assertEquals(3, response.leaveTypeId());
        assertEquals("CASUAL", response.leaveType());
        assertEquals("vivek@cresen.com", response.emailId());
    }

    @Test
    void shouldCreateLeaveTypeForAdminConfiguredCatalog() {
        when(leaveTypeRepository.existsByLeaveNameIgnoreCase("Work From Home")).thenReturn(false);
        when(leaveTypeRepository.existsByLeaveUniqueNameIgnoreCase("WORK_FROM_HOME")).thenReturn(false);
        when(leaveTypeRepository.save(any(LeaveType.class))).thenAnswer(invocation -> {
            LeaveType leaveType = invocation.getArgument(0);
            leaveType.updateDetails("Work From Home", "WORK_FROM_HOME", "Remote working days.", 24);
            return leaveType;
        });

        LeaveTypeResponse response = leaveService.createLeaveType(new CreateLeaveTypeRequest(
                "Work From Home",
                "work from home",
                "Remote working days.",
                24
        ));

        assertEquals("Work From Home", response.leaveName());
        assertEquals("WORK_FROM_HOME", response.leaveUniqueName());
        assertEquals(24, response.maxDays());
    }

    @Test
    void shouldRejectDuplicateLeaveTypeUniqueName() {
        when(leaveTypeRepository.existsByLeaveNameIgnoreCase("Sick Leave")).thenReturn(false);
        when(leaveTypeRepository.existsByLeaveUniqueNameIgnoreCase("SICK_LEAVE")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> leaveService.createLeaveType(new CreateLeaveTypeRequest(
                "Sick Leave",
                "sick leave",
                "Medical leave",
                12
        )));
    }

    @Test
    void shouldUpdateLeaveTypeUsingPopupWorkflow() {
        LeaveType leaveType = new LeaveType(5, "Casual Leave", "CASUAL_LEAVE");
        leaveType.updateDetails("Casual Leave", "CASUAL_LEAVE", "Short notice leave.", 7);

        when(leaveTypeRepository.findById(5)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.existsByLeaveNameIgnoreCaseAndIdNot("Privilege Leave", 5)).thenReturn(false);
        when(leaveTypeRepository.existsByLeaveUniqueNameIgnoreCaseAndIdNot("PRIVILEGE_LEAVE", 5)).thenReturn(false);
        when(leaveTypeRepository.save(any(LeaveType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveTypeResponse response = leaveService.updateLeaveType(5, new CreateLeaveTypeRequest(
                "Privilege Leave",
                "privilege leave",
                "Annual earned leave.",
                18
        ));

        assertEquals(5, response.id());
        assertEquals("Privilege Leave", response.leaveName());
        assertEquals("PRIVILEGE_LEAVE", response.leaveUniqueName());
        assertEquals(18, response.maxDays());
    }

    @Test
    void shouldDeleteLeaveType() {
        LeaveType leaveType = new LeaveType(8, "Optional Leave", "OPTIONAL_LEAVE");

        when(leaveTypeRepository.findById(8)).thenReturn(Optional.of(leaveType));

        leaveService.deleteLeaveType(8);

        verify(leaveRepository).saveAll(any());
        verify(leaveTypeRepository).delete(leaveType);
    }
}
