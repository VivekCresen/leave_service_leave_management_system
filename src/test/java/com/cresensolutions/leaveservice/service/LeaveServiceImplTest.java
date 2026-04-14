package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.*;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.*;
import com.cresensolutions.leaveservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceImplTest {

    @Mock private LeaveRepository leaveRepository;
    @Mock private LeaveDateRepository leaveDateRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private LeaveNotifyUserRepository leaveNotifyUserRepository;
    @Mock private LeaveEmailService leaveEmailService;
    @Mock private LeaveReminderDispatchService leaveReminderDispatchService;
    @Mock private Executor leaveTaskExecutor;
    @Mock private org.flowable.engine.RuntimeService runtimeService;
    @Mock private org.flowable.engine.TaskService taskService;

    @InjectMocks
    private LeaveServiceImpl leaveService;

    private UserProfile activeUser;
    private LeaveType leaveType;
    private LeaveRecord leaveRecord;

    @BeforeEach
    void setUp() throws Exception {
        activeUser = new UserProfile();
        setField(activeUser, "id", 1L);
        setField(activeUser, "userName", "john");
        setField(activeUser, "fullName", "John Doe");
        setField(activeUser, "emailId", "john@example.com");
        setField(activeUser, "active", true);
        setField(activeUser, "gender", "MALE");
        setField(activeUser, "role", "EMPLOYEE");
        setField(activeUser, "createdBy", "manager1");

        leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", "Annual leave", 20, null);
        setField(leaveType, "id", 1);

        leaveRecord = new LeaveRecord(activeUser, leaveType, "Vacation", null, null, true);
        setField(leaveRecord, "id", 10L);
        setField(leaveRecord, "status", "PENDING");
    }

    @Test
    void createLeave_byUserId_success() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(1L);
        verify(leaveRepository, atLeastOnce()).save(any(LeaveRecord.class));
    }

    @Test
    void createLeave_byUsername_success() throws Exception {
        CreateLeaveRequest req = new CreateLeaveRequest(
                null, "john", 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Sick", null, null, null, null);

        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(5.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
    }

    @Test
    void createLeave_noUserIdOrUsername_throwsIllegalArgument() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                null, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Sick", null, null, null, null);

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either userId or username must be provided");
    }

    @Test
    void createLeave_emptyLeaveDates_throwsIllegalArgument() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1, List.of(), "Sick", null, null, null, null);

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one leave date is required");
    }

    @Test
    void createLeave_userNotFound_throwsResourceNotFound() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                99L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Sick", null, null, null, null);

        when(userProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 99");
    }

    @Test
    void createLeave_leaveTypeNotFound_throwsResourceNotFound() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 99,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Sick", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave type not found with id: 99");
    }

    @Test
    void createLeave_inactiveUser_throwsIllegalArgument() throws Exception {
        setField(activeUser, "active", false);
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Sick", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inactive users cannot submit leave requests");
    }

    @Test
    void createLeave_genderRestriction_throwsIllegalArgument() throws Exception {
        LeaveType femaleOnly = new LeaveType("Maternity", "MATERNITY", "Maternity leave", 90, "FEMALE");
        setField(femaleOnly, "id", 2);

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 2,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Maternity", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(2)).thenReturn(Optional.of(femaleOnly));

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only available for Female employees");
    }

    @Test
    void createLeave_noRemainingBalance_throwsIllegalArgument() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(0.0);

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no remaining");
    }

    @Test
    void createLeave_withNotifyUsers_savesNotifyUsers() throws Exception {
        UserProfile notifyUser = new UserProfile();
        setField(notifyUser, "id", 2L);

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, List.of(2L));

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(notifyUser));
        when(leaveNotifyUserRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
        verify(leaveNotifyUserRepository).saveAll(any());
    }

    // ─── getLeaveById ────────────────────────────────────────────────────────────

    @Test
    void getLeaveById_found_returnsResponse() {
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.getLeaveById(10L);
        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void getLeaveById_notFound_throwsResourceNotFound() {
        when(leaveRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.getLeaveById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave not found with id: 99");
    }

    // ─── getAllLeaves ─────────────────────────────────────────────────────────────

    @Test
    void getAllLeaves_returnsMappedPage() {
        Page<LeaveRecord> page = new PageImpl<>(List.of(leaveRecord));
        when(leaveRepository.findAllPaged(any(Pageable.class))).thenReturn(page);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        Page<LeaveResponse> result = leaveService.getAllLeaves(0, 10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── getLeavesByUserId ────────────────────────────────────────────────────────

    @Test
    void getLeavesByUserId_withContent_returnsPage() {
        Page<LeaveRecord> page = new PageImpl<>(List.of(leaveRecord));
        when(leaveRepository.findByUserIdPaged(eq(1L), any())).thenReturn(page);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        Page<LeaveResponse> result = leaveService.getLeavesByUserId(1L, 0, 10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getLeavesByUserId_emptyAndUserNotFound_throwsResourceNotFound() {
        when(leaveRepository.findByUserIdPaged(eq(99L), any())).thenReturn(Page.empty());
        when(userProfileRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> leaveService.getLeavesByUserId(99L, 0, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 99");
    }

    @Test
    void getLeavesByUserId_emptyButUserExists_returnsEmptyPage() {
        when(leaveRepository.findByUserIdPaged(eq(1L), any())).thenReturn(Page.empty());
        when(userProfileRepository.existsById(1L)).thenReturn(true);

        Page<LeaveResponse> result = leaveService.getLeavesByUserId(1L, 0, 10);
        assertThat(result.isEmpty()).isTrue();
    }

    // ─── getLeavesByUsername ──────────────────────────────────────────────────────

    @Test
    void getLeavesByUsername_success() {
        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        Page<LeaveRecord> page = new PageImpl<>(List.of(leaveRecord));
        when(leaveRepository.findByUsernamePaged(eq("john"), any())).thenReturn(page);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        Page<LeaveResponse> result = leaveService.getLeavesByUsername("john", 0, 10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getLeavesByUsername_userNotFound_throwsResourceNotFound() {
        when(userProfileRepository.findByUserName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.getLeavesByUsername("unknown", 0, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with username: unknown");
    }

    @Test
    void getLeavesByUsername_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> leaveService.getLeavesByUsername("  ", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }

    // ─── getLeavesByManagerUsername ───────────────────────────────────────────────

    @Test
    void getLeavesByManagerUsername_success() {
        Page<LeaveRecord> page = new PageImpl<>(List.of(leaveRecord));
        when(leaveRepository.findByManagerUsernamePaged(eq("manager1"), any())).thenReturn(page);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        Page<LeaveResponse> result = leaveService.getLeavesByManagerUsername("manager1", 0, 10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getLeavesByManagerUsername_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> leaveService.getLeavesByManagerUsername("", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manager username is required");
    }

    // ─── updateLeaveStatus ────────────────────────────────────────────────────────

    @Test
    void updateLeaveStatus_approved_success() {
        LeaveDate ld = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(ld));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(leaveTaskExecutor).execute(any(Runnable.class));
        when(leaveTypeRepository.findUniqueNameById(any())).thenReturn("ANNUAL_LEAVE");

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(leaveEmailService).sendLeaveStatusNotification(any(), any(), any(), any(), any(), eq("APPROVED"), any(), any());
    }

    @Test
    void updateLeaveStatus_rejected_withReason_success() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "REJECTED", "Not enough notice");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(leaveEmailService).sendLeaveStatusNotification(any(), any(), any(), any(), any(), eq("REJECTED"), any(), eq("Not enough notice"));
    }

    @Test
    void updateLeaveStatus_rejected_noReason_throwsIllegalArgument() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "REJECTED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void updateLeaveStatus_leaveNotFound_throwsResourceNotFound() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "APPROVED", null);
        when(leaveRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(99L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave not found with id: 99");
    }

    // ─── updateLeave ──────────────────────────────────────────────────────────────

    @Test
    void updateLeave_success() {
        UpdateLeaveRequest req = new UpdateLeaveRequest(
                1, List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Updated reason", null, null, null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.updateLeave(10L, req);
        assertThat(response).isNotNull();
        verify(leaveDateRepository).deleteByApplicationId(10L);
        verify(leaveNotifyUserRepository).deleteByLeaveId(10L);
    }

    @Test
    void updateLeave_notPendingStatus_throwsIllegalArgument() throws Exception {
        setField(leaveRecord, "status", "APPROVED");
        UpdateLeaveRequest req = new UpdateLeaveRequest(
                1, List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Updated reason", null, null, null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.updateLeave(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PENDING leave applications can be edited");
    }

    @Test
    void updateLeave_emptyDates_throwsIllegalArgument() {
        UpdateLeaveRequest req = new UpdateLeaveRequest(1, List.of(), "reason", null, null, null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.updateLeave(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one leave date is required");
    }

    @Test
    void updateLeave_leaveTypeNotFound_throwsResourceNotFound() {
        UpdateLeaveRequest req = new UpdateLeaveRequest(
                99, List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "reason", null, null, null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveTypeRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.updateLeave(10L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave type not found with id: 99");
    }

    // ─── deletePendingLeave ───────────────────────────────────────────────────────

    @Test
    void deletePendingLeave_success() {
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(leaveRecord));

        leaveService.deletePendingLeave(10L);

        verify(leaveNotifyUserRepository).deleteByLeaveId(10L);
        verify(leaveDateRepository).deleteByApplicationId(10L);
        verify(leaveRepository).deleteById(10L);
    }

    @Test
    void deletePendingLeave_notFound_throwsResourceNotFound() {
        when(leaveRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.deletePendingLeave(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave not found with id: 99");
    }

    @Test
    void deletePendingLeave_notPending_throwsIllegalArgument() throws Exception {
        setField(leaveRecord, "status", "APPROVED");
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.deletePendingLeave(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PENDING leave applications can be deleted");
    }

    // ─── getLeaveTypes ────────────────────────────────────────────────────────────

    @Test
    void getLeaveTypes_returnsList() {
        when(leaveTypeRepository.findAllOrderedById()).thenReturn(List.of(leaveType));

        List<LeaveTypeResponse> result = leaveService.getLeaveTypes();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).leaveName()).isEqualTo("Annual Leave");
    }

    // ─── createLeaveType ─────────────────────────────────────────────────────────

    @Test
    void createLeaveType_success() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Sick Leave", "sick_leave", "For illness", 10, null);

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenReturn(
                new LeaveType("Sick Leave", "SICK_LEAVE", "For illness", 10, null));

        LeaveTypeResponse response = leaveService.createLeaveType(req);
        assertThat(response.leaveName()).isEqualTo("Sick Leave");
    }

    @Test
    void createLeaveType_duplicateName_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Annual Leave", "ANNUAL_LEAVE", null, 20, null);

        LeaveType conflict = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of(conflict));

        assertThatThrownBy(() -> leaveService.createLeaveType(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leave name already exists");
    }

    @Test
    void createLeaveType_duplicateUniqueName_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "New Leave", "ANNUAL_LEAVE", null, 5, null);

        LeaveType conflict = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of(conflict));

        assertThatThrownBy(() -> leaveService.createLeaveType(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createLeaveType_nullMaxDays_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Sick Leave", "SICK_LEAVE", null, null, null);

        assertThatThrownBy(() -> leaveService.createLeaveType(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max days is required");
    }

    // ─── updateLeaveType ─────────────────────────────────────────────────────────

    @Test
    void updateLeaveType_success() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Annual Leave Updated", "ANNUAL_LEAVE", "Updated desc", 25, null);

        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.findConflictsForUpdate(eq(1), any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenReturn(leaveType);

        LeaveTypeResponse response = leaveService.updateLeaveType(1, req);
        assertThat(response).isNotNull();
    }

    @Test
    void updateLeaveType_notFound_throwsResourceNotFound() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "X", "X_LEAVE", null, 5, null);

        when(leaveTypeRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.updateLeaveType(99, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave type not found with id: 99");
    }

    // ─── deleteLeaveType ─────────────────────────────────────────────────────────

    @Test
    void deleteLeaveType_success() {
        when(leaveTypeRepository.existsById(1)).thenReturn(true);

        leaveService.deleteLeaveType(1);

        verify(leaveRepository).clearLeaveTypeReferenceByLeaveTypeId(1);
        verify(leaveTypeRepository).deleteById(1);
    }

    @Test
    void deleteLeaveType_notFound_throwsResourceNotFound() {
        when(leaveTypeRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> leaveService.deleteLeaveType(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave type not found with id: 99");
    }

    // ─── getNotifyUsers ───────────────────────────────────────────────────────────

    @Test
    void getNotifyUsers_employee_returnsTeammates() throws Exception {
        UserProfile teammate = new UserProfile();
        setField(teammate, "id", 2L);
        setField(teammate, "fullName", "Jane");
        setField(teammate, "emailId", "jane@example.com");
        setField(teammate, "role", "EMPLOYEE");

        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findActiveByManagerUsername("manager1"))
                .thenReturn(List.of(activeUser, teammate));

        List<NotifyUserResponse> result = leaveService.getNotifyUsers("john");
        // should exclude self (id=1), return only teammate
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Jane");
    }

    @Test
    void getNotifyUsers_manager_returnsAllActiveExceptSelf() throws Exception {
        setField(activeUser, "role", "MANAGER");
        UserProfile emp = new UserProfile();
        setField(emp, "id", 3L);
        setField(emp, "fullName", "Bob");
        setField(emp, "emailId", "bob@example.com");
        setField(emp, "role", "EMPLOYEE");

        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findAllActiveExcept(1L)).thenReturn(List.of(emp));

        List<NotifyUserResponse> result = leaveService.getNotifyUsers("john");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Bob");
    }

    @Test
    void getNotifyUsers_admin_returnsAllActiveExceptSelf() throws Exception {
        setField(activeUser, "role", "ADMIN");
        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findAllActiveExcept(1L)).thenReturn(List.of());

        List<NotifyUserResponse> result = leaveService.getNotifyUsers("john");
        assertThat(result).isEmpty();
    }

    @Test
    void getNotifyUsers_employeeNoManager_returnsEmpty() throws Exception {
        setField(activeUser, "createdBy", null);
        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));

        List<NotifyUserResponse> result = leaveService.getNotifyUsers("john");
        assertThat(result).isEmpty();
    }

    @Test
    void getNotifyUsers_userNotFound_throwsResourceNotFound() {
        when(userProfileRepository.findByUserName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.getNotifyUsers("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found: ghost");
    }

    @Test
    void getNotifyUsers_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> leaveService.getNotifyUsers(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }

    // ─── helper ──────────────────────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + target.getClass());
    }
}
