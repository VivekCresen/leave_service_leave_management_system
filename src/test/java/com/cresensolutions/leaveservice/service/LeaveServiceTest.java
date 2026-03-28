package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
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
import static org.mockito.ArgumentMatchers.any;
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
}
