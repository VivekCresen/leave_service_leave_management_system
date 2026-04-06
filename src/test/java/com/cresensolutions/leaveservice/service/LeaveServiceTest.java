package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
        when(leaveTypeRepository.findConflicts("Work From Home", "WORK_FROM_HOME")).thenReturn(List.of());
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
        when(leaveTypeRepository.findConflicts("Sick Leave", "SICK_LEAVE"))
                .thenReturn(List.of(new LeaveType(3, "Another", "SICK_LEAVE")));

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
        when(leaveTypeRepository.findConflictsForUpdate(5, "Privilege Leave", "PRIVILEGE_LEAVE"))
                .thenReturn(List.of());
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

        verify(leaveRepository).clearLeaveTypeReferenceByLeaveTypeId(8);
        verify(leaveTypeRepository).delete(leaveType);
    }

    @Test
    void shouldReturnPagedLeavesWithoutExtraUserLookupWhenDataExists() {
        UserProfile user = new UserProfileTestBuilder()
                .id(10L)
                .fullName("Vivek Chavda")
                .emailId("vivek@cresen.com")
                .active(true)
                .build();
        LeaveType leaveType = new LeaveType(3, "Casual Leave", "CASUAL");
        LeaveRecord leaveRecord = new LeaveRecord(
                user,
                leaveType,
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 4),
                "Family event",
                "Need 3 days",
                "{\"status\":\"submitted\"}",
                true
        );

        when(leaveRepository.findAllByUserIdOrderByFromDateDescIdDesc(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(leaveRecord)));

        Page<LeaveResponse> response = leaveService.getLeavesByUserId(10L, 0, 50);

        assertEquals(1, response.getTotalElements());
        verify(userProfileRepository, org.mockito.Mockito.never()).existsById(10L);
    }

    @Test
    void shouldRejectInvalidLeaveDateRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.createLeave(new CreateLeaveRequest(
                        10L,
                        3,
                        LocalDate.of(2026, 4, 5),
                        LocalDate.of(2026, 4, 4),
                        "Family event",
                        "Need 1 day",
                        "{}",
                        true
                ))
        );

        assertEquals("To date must be on or after from date.", exception.getMessage());
    }

    @Test
    void shouldRejectInactiveUserLeaveCreation() {
        UserProfile inactiveUser = new UserProfileTestBuilder().id(10L).emailId("vivek@cresen.com").active(false).build();

        when(userProfileRepository.findById(10L)).thenReturn(Optional.of(inactiveUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.createLeave(new CreateLeaveRequest(
                        10L,
                        3,
                        LocalDate.of(2026, 4, 2),
                        LocalDate.of(2026, 4, 4),
                        "Family event",
                        "Need 3 days",
                        "{}",
                        true
                ))
        );

        assertEquals("Inactive users cannot submit leave requests.", exception.getMessage());
    }

    @Test
    void shouldRejectUnknownLeaveTypeDuringCreation() {
        UserProfile activeUser = new UserProfileTestBuilder().id(10L).emailId("vivek@cresen.com").active(true).build();

        when(userProfileRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(3)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> leaveService.createLeave(new CreateLeaveRequest(
                        10L,
                        3,
                        LocalDate.of(2026, 4, 2),
                        LocalDate.of(2026, 4, 4),
                        "Family event",
                        "Need 3 days",
                        "{}",
                        null
                ))
        );

        assertEquals("Leave type not found with id: 3", exception.getMessage());
    }

    @Test
    void shouldDefaultEditableToTrueAndTrimReasonWhenCreatingLeave() {
        UserProfile activeUser = new UserProfileTestBuilder()
                .id(10L)
                .fullName("Vivek Chavda")
                .emailId("vivek@cresen.com")
                .active(true)
                .build();
        LeaveType leaveType = new LeaveType(3, "Casual Leave", "CASUAL");

        when(userProfileRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(3)).thenReturn(Optional.of(leaveType));
        when(leaveRepository.save(any(LeaveRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveResponse response = leaveService.createLeave(new CreateLeaveRequest(
                10L,
                3,
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 4),
                "  Family event  ",
                "Need 3 days",
                "{}",
                null
        ));

        assertEquals("Family event", response.reason());
        assertEquals(true, response.editable());
    }

    @Test
    void shouldReturnLeaveById() {
        UserProfile user = new UserProfileTestBuilder().id(10L).fullName("Vivek Chavda").emailId("vivek@cresen.com").active(true).build();
        LeaveType leaveType = new LeaveType(3, "Casual Leave", "CASUAL");
        LeaveRecord leaveRecord = new LeaveRecord(
                user,
                leaveType,
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 4),
                "Family event",
                "Need 3 days",
                "{}",
                false
        );

        when(leaveRepository.findDetailedById(21L)).thenReturn(Optional.of(leaveRecord));

        LeaveResponse response = leaveService.getLeaveById(21L);

        assertEquals("Vivek Chavda", response.fullName());
        assertFalse(response.editable());
    }

    @Test
    void shouldRejectMissingLeaveById() {
        when(leaveRepository.findDetailedById(21L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> leaveService.getLeaveById(21L));

        assertEquals("Leave not found with id: 21", exception.getMessage());
    }

    @Test
    void shouldSanitizePaginationWhenFetchingAllLeaves() {
        when(leaveRepository.findAllByOrderByFromDateDescIdDesc(any(Pageable.class)))
                .thenReturn(Page.empty());

        leaveService.getAllLeaves(-2, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(leaveRepository).findAllByOrderByFromDateDescIdDesc(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void shouldReturnLeaveTypesInOrder() {
        when(leaveTypeRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                new LeaveType(1, "Casual Leave", "CASUAL"),
                new LeaveType(2, "Sick Leave", "SICK")
        ));

        List<LeaveTypeResponse> response = leaveService.getLeaveTypes();

        assertEquals(2, response.size());
        assertEquals("CASUAL", response.get(0).leaveUniqueName());
        assertEquals("SICK", response.get(1).leaveUniqueName());
    }

    @Test
    void shouldRejectMissingUserWhenLeavePageIsEmpty() {
        when(leaveRepository.findAllByUserIdOrderByFromDateDescIdDesc(any(Long.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(userProfileRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> leaveService.getLeavesByUserId(99L, 0, 50)
        );

        assertEquals("User not found with id: 99", exception.getMessage());
    }

    @Test
    void shouldReturnEmptyLeavePageForExistingUser() {
        when(leaveRepository.findAllByUserIdOrderByFromDateDescIdDesc(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userProfileRepository.existsById(10L)).thenReturn(true);

        Page<LeaveResponse> response = leaveService.getLeavesByUserId(10L, 0, 50);

        assertEquals(0, response.getTotalElements());
        verify(userProfileRepository).existsById(10L);
    }

    @Test
    void shouldRejectMissingMaxDaysWhenCreatingLeaveType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.createLeaveType(new CreateLeaveTypeRequest("Casual Leave", "casual leave", "desc", null))
        );

        assertEquals("Max days is required.", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateLeaveNameWhenCreatingLeaveType() {
        when(leaveTypeRepository.findConflicts("Casual Leave", "CASUAL_LEAVE"))
                .thenReturn(List.of(new LeaveType(3, "Casual Leave", "OTHER")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.createLeaveType(new CreateLeaveTypeRequest("Casual Leave", "casual leave", "desc", 10))
        );

        assertEquals("Leave name already exists: Casual Leave", exception.getMessage());
    }

    @Test
    void shouldNormalizeDescriptionToNullWhenCreatingLeaveType() {
        when(leaveTypeRepository.findConflicts("Optional Leave", "OPTIONAL_LEAVE")).thenReturn(List.of());
        when(leaveTypeRepository.save(any(LeaveType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(
                new CreateLeaveTypeRequest("Optional Leave", "optional leave", "   ", 5)
        );

        assertNull(response.description());
    }

    @Test
    void shouldRejectMissingLeaveTypeOnUpdate() {
        when(leaveTypeRepository.findById(9)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> leaveService.updateLeaveType(9, new CreateLeaveTypeRequest("Optional Leave", "optional leave", "desc", 5))
        );

        assertEquals("Leave type not found with id: 9", exception.getMessage());
    }

    @Test
    void shouldRejectMissingMaxDaysOnUpdate() {
        when(leaveTypeRepository.findById(9)).thenReturn(Optional.of(new LeaveType(9, "Optional Leave", "OPTIONAL_LEAVE")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.updateLeaveType(9, new CreateLeaveTypeRequest("Optional Leave", "optional leave", "desc", null))
        );

        assertEquals("Max days is required.", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateLeaveUniqueNameOnUpdate() {
        LeaveType leaveType = new LeaveType(9, "Optional Leave", "OPTIONAL_LEAVE");
        when(leaveTypeRepository.findById(9)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.findConflictsForUpdate(9, "Optional Leave", "SICK_LEAVE"))
                .thenReturn(List.of(new LeaveType(4, "Sick Leave", "SICK_LEAVE")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.updateLeaveType(9, new CreateLeaveTypeRequest("Optional Leave", "sick leave", "desc", 5))
        );

        assertEquals("Leave unique name already exists: SICK_LEAVE", exception.getMessage());
    }

    @Test
    void shouldRejectMissingLeaveTypeOnDelete() {
        when(leaveTypeRepository.findById(7)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> leaveService.deleteLeaveType(7));

        assertEquals("Leave type not found with id: 7", exception.getMessage());
        verify(leaveRepository, never()).clearLeaveTypeReferenceByLeaveTypeId(7);
    }
}
