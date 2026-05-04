package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.*;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.messaging.LeaveEventPublisher;
import com.cresensolutions.leaveservice.model.*;
import com.cresensolutions.leaveservice.repository.*;
import com.cresensolutions.leaveservice.service.Impl.LeaveServiceImpl;
import com.cresensolutions.leaveservice.common.LeaveConstants;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
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
    @Mock private LeaveBalanceService leaveBalanceService;
    @Mock private Executor leaveTaskExecutor;
    @Mock private org.flowable.engine.RuntimeService runtimeService;
    @Mock private org.flowable.engine.TaskService taskService;
    @Mock private LeaveEventPublisher eventPublisher;

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
        setField(activeUser, "emailId", "john@cresensolutions.com");
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
    void createLeave_requestedDaysExceedRemainingBalance_throwsIllegalArgument() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(0.5);

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds remaining")
                .hasMessageContaining("1.0")
                .hasMessageContaining("0.5");
    }

    @Test
    void createLeave_halfDayWithinRemainingBalance_succeeds() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "MORNING_HALF")),
                "Vacation", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(0.5);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);

        assertThat(response).isNotNull();
        verify(leaveRepository, atLeastOnce()).save(any(LeaveRecord.class));
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

    @Test
    void getAllLeaves_returnsMappedPage() {
        Page<LeaveRecord> page = new PageImpl<>(List.of(leaveRecord));
        when(leaveRepository.findAllPaged(any(Pageable.class))).thenReturn(page);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        Page<LeaveResponse> result = leaveService.getAllLeaves(0, 10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }


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


    @Test
    void updateLeaveStatus_approved_success() throws Exception {
        LeaveDate ld = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("admin1", "APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(ld));

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishBalanceDeduct(any(), any(), anyDouble(), any());
        verify(eventPublisher).publishLeaveApproved(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void updateLeaveStatus_rejected_withReason_success() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "REJECTED", "Not enough notice");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishLeaveRejected(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("Not enough notice"));
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

    @Test
    void updateLeaveStatus_managerApproved_withFlowableTask_completesManagerTask() throws Exception {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "MANAGER_APPROVED", null);
        Task managerTask = mock(Task.class);
        TaskQuery taskQuery = mock(TaskQuery.class);

        when(managerTask.getId()).thenReturn("task-manager-1");
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey("10")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("task_manager_approval")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(managerTask);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);

        assertThat(response).isNotNull();
        verify(taskService).complete(eq("task-manager-1"), ArgumentMatchers.<Map<String, Object>>argThat(vars ->
                "manager1".equals(vars.get("actorUsername")) && "APPROVED".equals(vars.get("status"))));
        verify(leaveRepository, never()).save(any(LeaveRecord.class));
    }

    @Test
    void updateLeaveStatus_unsupportedStatus_throwsIllegalArgument() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "ON_HOLD", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported status: ON_HOLD");
    }

    @Test
    void reviewLeaveFromMail_managerApprove_pendingLeave_returnsManagerApproved() throws Exception {
        UserProfile manager = new UserProfile();
        setField(manager, "id", 2L);
        setField(manager, "userName", "manager1");
        setField(manager, "active", true);
        setField(manager, "role", "MANAGER");

        MailLeaveDecisionRequest request = new MailLeaveDecisionRequest("manager1", "APPROVED", null);
        Task task = mock(Task.class);
        TaskQuery taskQuery = mock(TaskQuery.class);

        when(task.getId()).thenReturn("mail-manager-task");
        when(task.getAssignee()).thenReturn("manager1");
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(manager));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord), Optional.of(leaveRecord));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey("10")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("task_manager_approval")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.reviewLeaveFromMail(10L, request);

        assertThat(response).isNotNull();
        verify(taskService).complete(eq("mail-manager-task"), ArgumentMatchers.<Map<String, Object>>argThat(vars ->
                "manager1".equals(vars.get("actorUsername")) && "APPROVED".equals(vars.get("status"))));
    }

    @Test
    void reviewLeaveFromMail_adminReject_managerApprovedLeave_defaultsReason() throws Exception {
        UserProfile admin = new UserProfile();
        setField(admin, "id", 3L);
        setField(admin, "userName", "admin1");
        setField(admin, "active", true);
        setField(admin, "role", "ADMIN");
        setField(leaveRecord, "status", "MANAGER_APPROVED");

        MailLeaveDecisionRequest request = new MailLeaveDecisionRequest("admin1", "REJECTED", "  ");
        Task task = mock(Task.class);
        TaskQuery taskQuery = mock(TaskQuery.class);

        when(task.getId()).thenReturn("mail-admin-task");
        when(userProfileRepository.findByUserNameIgnoreCase("admin1")).thenReturn(Optional.of(admin));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord), Optional.of(leaveRecord));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey("10")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("task_admin_approval")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.reviewLeaveFromMail(10L, request);

        assertThat(response).isNotNull();
        verify(taskService).complete(eq("mail-admin-task"), ArgumentMatchers.<Map<String, Object>>argThat(vars ->
                "admin1".equals(vars.get("actorUsername"))
                        && "REJECTED".equals(vars.get("status"))
                        && "Rejected from email approval link.".equals(vars.get("rejectionReason"))));
    }

    @Test
    void reviewLeaveFromMail_inactiveActor_throwsIllegalArgument() throws Exception {
        UserProfile inactiveManager = new UserProfile();
        setField(inactiveManager, "userName", "manager1");
        setField(inactiveManager, "active", false);
        setField(inactiveManager, "role", "MANAGER");

        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(inactiveManager));

        assertThatThrownBy(() -> leaveService.reviewLeaveFromMail(
                10L, new MailLeaveDecisionRequest("manager1", "APPROVED", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inactive users cannot approve or reject leave requests");
    }

    @Test
    void reviewLeaveFromMail_managerAssignedToSomeoneElse_throwsIllegalArgument() throws Exception {
        UserProfile manager = new UserProfile();
        setField(manager, "id", 2L);
        setField(manager, "userName", "manager1");
        setField(manager, "active", true);
        setField(manager, "role", "MANAGER");

        MailLeaveDecisionRequest request = new MailLeaveDecisionRequest("manager1", "APPROVED", null);
        Task task = mock(Task.class);
        TaskQuery taskQuery = mock(TaskQuery.class);

        when(task.getAssignee()).thenReturn("other_manager");
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(manager));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey("10")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("task_manager_approval")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        assertThatThrownBy(() -> leaveService.reviewLeaveFromMail(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigned to other_manager");
    }

    @Test
    void reviewLeaveFromMail_nonAdminOnManagerApprovedLeave_throwsIllegalArgument() throws Exception {
        UserProfile manager = new UserProfile();
        setField(manager, "id", 2L);
        setField(manager, "userName", "manager1");
        setField(manager, "active", true);
        setField(manager, "role", "MANAGER");
        setField(leaveRecord, "status", "MANAGER_APPROVED");

        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(manager));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.reviewLeaveFromMail(
                10L, new MailLeaveDecisionRequest("manager1", "APPROVED", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only an ADMIN can give final approval");
    }

    @Test
    void reviewLeaveFromMail_alreadyApprovedLeave_throwsIllegalArgument() throws Exception {
        UserProfile admin = new UserProfile();
        setField(admin, "id", 3L);
        setField(admin, "userName", "admin1");
        setField(admin, "active", true);
        setField(admin, "role", "ADMIN");
        setField(leaveRecord, "status", "APPROVED");

        when(userProfileRepository.findByUserNameIgnoreCase("admin1")).thenReturn(Optional.of(admin));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.reviewLeaveFromMail(
                10L, new MailLeaveDecisionRequest("admin1", "REJECTED", "late")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already APPROVED");
    }


    @Test
    void updateLeave_success() {
        UpdateLeaveRequest req = new UpdateLeaveRequest(
                1, List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Updated reason", null, null, null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
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

    @Test
    void updateLeave_requestedDaysExceedRemainingBalance_throwsIllegalArgument() {
        UpdateLeaveRequest req = new UpdateLeaveRequest(
                1, List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "reason", null, null, null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(0.5);

        assertThatThrownBy(() -> leaveService.updateLeave(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds remaining")
                .hasMessageContaining("1.0")
                .hasMessageContaining("0.5");
    }


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


    @Test
    void getLeaveTypes_returnsList() {
        when(leaveTypeRepository.findAllOrderedById()).thenReturn(List.of(leaveType));

        List<LeaveTypeResponse> result = leaveService.getLeaveTypes();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).leaveName()).isEqualTo("Annual Leave");
    }


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


    @Test
    void getNotifyUsers_employee_returnsTeammates() throws Exception {
        UserProfile teammate = new UserProfile();
        setField(teammate, "id", 2L);
        setField(teammate, "fullName", "Jane");
        setField(teammate, "emailId", "jane@cresensolutions.com");
        setField(teammate, "role", "EMPLOYEE");

        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findAllActiveExcept(1L)).thenReturn(List.of(teammate));

        List<NotifyUserResponse> result = leaveService.getNotifyUsers("john");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Jane");
    }

    @Test
    void getNotifyUsers_manager_returnsAllActiveExceptSelf() throws Exception {
        setField(activeUser, "role", "MANAGER");
        UserProfile emp = new UserProfile();
        setField(emp, "id", 3L);
        setField(emp, "fullName", "Bob");
        setField(emp, "emailId", "bob@cresensolutions.com");
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
        when(userProfileRepository.findAllActiveExcept(1L)).thenReturn(List.of());

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

    @Test
    void sendReminderEmail_usesReminderTypeAndFallsBackToUsername() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("reminderType")).thenReturn("4DAY");
        when(execution.getVariable("managerEmail")).thenReturn("manager@cresensolutions.com");
        when(execution.getVariable("adminEmail")).thenReturn("admin@cresensolutions.com");
        when(execution.getVariable("employeeName")).thenReturn(null);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("service_send_reminder_email");

        leaveService.sendReminderEmail(execution);

        verify(eventPublisher).publishLeaveReminder(
                eq(10L), eq("4DAY"),
                eq("manager@cresensolutions.com"), eq("admin@cresensolutions.com"),
                eq("john"), eq("Annual Leave"), eq("Vacation"),
                eq("proc-1"), eq("service_send_reminder_email")
        );
    }

    @Test
    void sendReminderEmail_withoutReminderType_skipsDispatch() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("reminderType")).thenReturn(null);

        leaveService.sendReminderEmail(execution);

        verifyNoInteractions(leaveReminderDispatchService);
    }

    @Test
    void updateLeaveStatusFromFlowable_updatesLeaveAndExecutionVariables() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn("REJECTED");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn("Not enough notice");
        when(execution.getProcessInstanceId()).thenReturn("proc-2");
        when(execution.getCurrentActivityId()).thenReturn("service_update_leave_status");
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any(LeaveRecord.class))).thenReturn(leaveRecord);

        leaveService.updateLeaveStatusFromFlowable(execution);

        assertThat(leaveRecord.getStatus()).isEqualTo("REJECTED");
        verify(leaveRepository).save(leaveRecord);
        verify(execution).setVariable("employeeName", "John Doe");
        verify(execution).setVariable("leaveType", "ANNUAL_LEAVE");
    }

    @Test
    void sendLeaveStatusMail_usesStatusVariableToSendNotification() {
        DelegateExecution execution = mock(DelegateExecution.class);
        LeaveDate leaveDate = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");

        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn("John Doe");
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(leaveDate));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        leaveService.sendLeaveStatusMail(execution);

        verify(eventPublisher).publishLeaveApproved(
                eq(10L), any(), any(), any(), any(), any(), any(),
                eq("manager1"), any(), anyDouble()
        );
    }



    @Test
    void updateLeaveStatus_approved_actorWithFullName_usesFullNameInEmail() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("admin1", "APPROVED", null);

        UserProfile admin = new UserProfile();
        setField(admin, "userName", "admin1");
        setField(admin, "fullName", "Admin One");
        setField(admin, "role", "ADMIN");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));

        leaveService.updateLeaveStatus(10L, req);

        verify(eventPublisher).publishLeaveApproved(any(), any(), any(), any(), any(), any(), any(),
                eq("Admin One"), eq("Administrator"), anyDouble());
    }

    @Test
    void updateLeaveStatus_approved_actorNotFound_usesUsernameAsDisplayName() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("ghost_admin", "APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("ghost_admin")).thenReturn(Optional.empty());

        leaveService.updateLeaveStatus(10L, req);

        verify(eventPublisher).publishLeaveApproved(any(), any(), any(), any(), any(), any(), any(),
                eq("ghost_admin"), eq(""), anyDouble());
    }

    @Test
    void updateLeaveStatus_approved_actorWithNullFullName_usesUsernameAsDisplayName() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("admin1", "APPROVED", null);

        UserProfile admin = new UserProfile();
        setField(admin, "userName", "admin1");
        setField(admin, "fullName", null);
        setField(admin, "role", "EMPLOYEE");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));

        leaveService.updateLeaveStatus(10L, req);

        verify(eventPublisher).publishLeaveApproved(any(), any(), any(), any(), any(), any(), any(),
                eq("admin1"), eq("Employee"), anyDouble());
    }

    @Test
    void updateLeaveStatus_approved_actorWithManagerRole_displaysManager() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("mgr1", "APPROVED", null);

        UserProfile mgr = new UserProfile();
        setField(mgr, "userName", "mgr1");
        setField(mgr, "fullName", "Manager One");
        setField(mgr, "role", "MANAGER");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("mgr1")).thenReturn(Optional.of(mgr));

        leaveService.updateLeaveStatus(10L, req);

        verify(eventPublisher).publishLeaveApproved(any(), any(), any(), any(), any(), any(), any(),
                eq("Manager One"), eq("Manager"), anyDouble());
    }

    @Test
    void updateLeaveStatus_approved_actorWithUnknownRole_displaysCapitalized() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("actor1", "APPROVED", null);

        UserProfile actor = new UserProfile();
        setField(actor, "userName", "actor1");
        setField(actor, "fullName", "Actor One");
        setField(actor, "role", "SUPERVISOR");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("actor1")).thenReturn(Optional.of(actor));

        leaveService.updateLeaveStatus(10L, req);

        verify(eventPublisher).publishLeaveApproved(any(), any(), any(), any(), any(), any(), any(),
                eq("Actor One"), eq("Supervisor"), anyDouble());
    }


    @Test
    void createLeaveType_withSpacesInUniqueName_normalizesToUpperUnderscore() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Sick Leave", "sick leave", "desc", 10, null);

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(req);

        assertThat(response.leaveUniqueName()).isEqualTo("SICK_LEAVE");
    }

    @Test
    void createLeaveType_blankUniqueName_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Sick Leave", "  ", "desc", 10, null);

        assertThatThrownBy(() -> leaveService.createLeaveType(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leave unique name is required");
    }

    @Test
    void createLeaveType_withBlankDescription_savesNullDescription() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Sick Leave", "SICK_LEAVE", "   ", 10, null);

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(req);

        assertThat(response.description()).isNull();
    }

    @Test
    void createLeaveType_withFemaleGenderRestriction_normalizesToFEMALE() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Maternity", "MATERNITY", null, 90, "female");

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(req);

        assertThat(response.genderRestriction()).isEqualTo("FEMALE");
    }

    @Test
    void createLeaveType_withMaleGenderRestriction_normalizesToMALE() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Paternity", "PATERNITY", null, 15, "male");

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(req);

        assertThat(response.genderRestriction()).isEqualTo("MALE");
    }

    @Test
    void createLeaveType_withInvalidGenderRestriction_savesNull() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Other Leave", "OTHER_LEAVE", null, 5, "OTHER");

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(req);

        assertThat(response.genderRestriction()).isNull();
    }

    @Test
    void createLeaveType_withNullGenderRestriction_savesNull() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Annual Leave", "ANNUAL_LEAVE_2", null, 20, null);

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveTypeResponse response = leaveService.createLeaveType(req);

        assertThat(response.genderRestriction()).isNull();
    }

    @Test
    void updateLeaveType_conflictOnUniqueName_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "New Leave", "ANNUAL_LEAVE", null, 5, null);

        LeaveType conflict = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.findConflictsForUpdate(eq(1), any(), any())).thenReturn(List.of(conflict));

        assertThatThrownBy(() -> leaveService.updateLeaveType(1, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }


    @Test
    void sendLeaveStatusMail_nullActorUsername_usesSystemDisplayName() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn("John");
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn(null);
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        leaveService.sendLeaveStatusMail(execution);

        verify(eventPublisher).publishLeaveApproved(eq(10L), any(), any(), any(), any(), any(), any(), any(), any(), anyDouble());
    }

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


    @Test
    void getBookedDates_returnsDateStrings() {
        when(leaveRepository.findBookedDatesByUsername("john"))
                .thenReturn(List.of("2026-05-01", "2026-05-02"));

        List<String> result = leaveService.getBookedDates("john");
        assertThat(result).containsExactly("2026-05-01", "2026-05-02");
    }

    @Test
    void getBookedDates_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> leaveService.getBookedDates("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void getBookedDates_noLeaves_returnsEmpty() {
        when(leaveRepository.findBookedDatesByUsername("john")).thenReturn(List.of());

        List<String> result = leaveService.getBookedDates("john");
        assertThat(result).isEmpty();
    }


    @Test
    void appendAuditTrailEntry_appendsAndReturnsResponse() {
        AppendAuditTrailRequest req = new AppendAuditTrailRequest(
                "CUSTOM_EVENT", "john", null, null, "Manual note");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.appendAuditTrailEntry(10L, req);
        assertThat(response).isNotNull();
        verify(leaveRepository).save(any(LeaveRecord.class));
    }

    @Test
    void appendAuditTrailEntry_leaveNotFound_throwsResourceNotFound() {
        AppendAuditTrailRequest req = new AppendAuditTrailRequest(
                "EVENT", "actor", null, null, "note");

        when(leaveRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.appendAuditTrailEntry(99L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave not found with id: 99");
    }


    @Test
    void updateLeaveStatus_managerApproved_noFlowableTask_setsStatusAndNotifiesAdmin() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "MANAGER_APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of());

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(leaveRepository).save(any(LeaveRecord.class));
    }

    @Test
    void updateLeaveStatus_managerApproved_wrongCurrentStatus_throwsIllegalArgument() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "MANAGER_APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void updateLeaveStatus_approved_wrongCurrentStatus_throwsIllegalArgument() {

        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("admin1", "APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MANAGER_APPROVED");
    }


    @Test
    void applyPartialStatus_managerAllApproved_setsManagerApprovedAndNotifiesAdmin() {
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "manager1",
                List.of(new PartialLeaveStatusRequest.DateDecision(
                        LocalDate.now(), "FULL", "APPROVED")),
                null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);
        assertThat(response).isNotNull();
        verify(leaveRepository).save(any(LeaveRecord.class));
    }

    @Test
    void applyPartialStatus_managerAllRejected_setsRejectedDirectly() {
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        setField2(d1, "id", 1L);
        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "manager1",
                List.of(new PartialLeaveStatusRequest.DateDecision(
                        LocalDate.now(), "FULL", "REJECTED")),
                "Not approved");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishLeaveRejected(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("Not approved"));
    }

    @Test
    void applyPartialStatus_adminFinalApproved_setsApprovedAndDeductsBalance() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "admin1",
                List.of(new PartialLeaveStatusRequest.DateDecision(
                        LocalDate.now(), "FULL", "APPROVED")),
                null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishBalanceDeduct(any(), any(), anyDouble(), any());
        verify(eventPublisher).publishPartialDecision(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void applyPartialStatus_wrongStatus_throwsIllegalArgument() throws Exception {
        setField(leaveRecord, "status", "APPROVED");
        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "admin1",
                List.of(new PartialLeaveStatusRequest.DateDecision(
                        LocalDate.now(), "FULL", "APPROVED")),
                null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThatThrownBy(() -> leaveService.applyPartialStatus(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partial review only allowed");
    }


    @Test
    void notifyAdminForFinalApproval_withAdminEmails_sendsNotification() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("employeeName")).thenReturn("John Doe");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        leaveService.notifyAdminForFinalApproval(execution);

        verify(eventPublisher).publishAdminNotify(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyAdminForFinalApproval_noAdminEmails_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.empty());

        leaveService.notifyAdminForFinalApproval(execution);

        verifyNoInteractions(leaveEmailService);
    }

    @Test
    void notifyAdminForFinalApproval_nullLeaveId_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(null);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");

        leaveService.notifyAdminForFinalApproval(execution);

        verifyNoInteractions(leaveEmailService);
    }


    @Test
    void calculateReminderSchedule_withFutureDates_setsBothTimers() {
        DelegateExecution execution = mock(DelegateExecution.class);
        String json = "[{\"date\":\"" + LocalDate.now().plusDays(10) + "\",\"dayType\":\"FULL\"}]";
        when(execution.getVariable("leaveDates")).thenReturn(json);

        leaveService.calculateReminderSchedule(execution);

        verify(execution).setVariable(eq("fourDayReminderTime"), any(Date.class));
        verify(execution).setVariable(eq("twoDayReminderTime"), any(Date.class));
    }

    @Test
    void calculateReminderSchedule_withNullLeaveDates_disablesTimers() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveDates")).thenReturn(null);

        leaveService.calculateReminderSchedule(execution);

        verify(execution).setVariable(eq("fourDayReminderTime"), any(Date.class));
        verify(execution).setVariable(eq("twoDayReminderTime"), any(Date.class));
    }

    @Test
    void calculateReminderSchedule_withPastDates_disablesTimers() {
        DelegateExecution execution = mock(DelegateExecution.class);
        String json = "[{\"date\":\"" + LocalDate.now().minusDays(5) + "\",\"dayType\":\"FULL\"}]";
        when(execution.getVariable("leaveDates")).thenReturn(json);

        leaveService.calculateReminderSchedule(execution);

        verify(execution).setVariable(eq("fourDayReminderTime"), any(Date.class));
        verify(execution).setVariable(eq("twoDayReminderTime"), any(Date.class));
    }

    @Test
    void calculateReminderSchedule_withInvalidJson_disablesTimers() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveDates")).thenReturn("not-valid-json");

        leaveService.calculateReminderSchedule(execution);

        verify(execution).setVariable(eq("fourDayReminderTime"), any(Date.class));
        verify(execution).setVariable(eq("twoDayReminderTime"), any(Date.class));
    }

    @Test
    void calculateReminderSchedule_dateWithin4Days_disables4DayTimer() {
        DelegateExecution execution = mock(DelegateExecution.class);
        String json = "[{\"date\":\"" + LocalDate.now().plusDays(3) + "\",\"dayType\":\"FULL\"}]";
        when(execution.getVariable("leaveDates")).thenReturn(json);

        leaveService.calculateReminderSchedule(execution);

        verify(execution).setVariable(eq("fourDayReminderTime"), any(Date.class));
        verify(execution).setVariable(eq("twoDayReminderTime"), any(Date.class));
    }

    @Test
    void calculateReminderSchedule_dateWithNullInJson_skipsNullDate() {
        DelegateExecution execution = mock(DelegateExecution.class);
        String json = "[{\"date\":null,\"dayType\":\"FULL\"},{\"date\":\""
                + LocalDate.now().plusDays(10) + "\",\"dayType\":\"FULL\"}]";
        when(execution.getVariable("leaveDates")).thenReturn(json);

        leaveService.calculateReminderSchedule(execution);

        verify(execution).setVariable(eq("fourDayReminderTime"), any(Date.class));
        verify(execution).setVariable(eq("twoDayReminderTime"), any(Date.class));
    }


    @Test
    void updateLeaveStatusFromFlowable_managerApproved_callsSetManagerApproved() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn("MANAGER_APPROVED");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("service_set_manager_approved");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);

        leaveService.updateLeaveStatusFromFlowable(execution);

        assertThat(leaveRecord.getStatus()).isEqualTo("MANAGER_APPROVED");
        verify(leaveRepository).save(leaveRecord);
    }

    private static void setField2(Object target, String fieldName, Object value) {
        try {
            setField(target, fieldName, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createLeave_nullLeaveUniqueName_skipsBalanceCheck() {
        LeaveType noUniqueNameType = new LeaveType("Special Leave", null, "desc", 5, null);
        setField2(noUniqueNameType, "id", 3);

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 3,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Special", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(3)).thenReturn(Optional.of(noUniqueNameType));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
        verify(employeeLeaveRepository, never()).getRemainingBalance(any(), any());
    }

    @Test
    void createLeave_blankLeaveUniqueName_skipsBalanceCheck() {
        LeaveType blankUniqueType = new LeaveType("Blank Unique", "  ", "desc", 5, null);
        setField2(blankUniqueType, "id", 4);

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 4,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Reason", null, null, true, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(4)).thenReturn(Optional.of(blankUniqueType));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
        verify(employeeLeaveRepository, never()).getRemainingBalance(any(), any());
    }

    @Test
    void createLeave_nullRemainingBalance_doesNotThrow() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(null);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
    }

    @Test
    void createLeave_editableNull_defaultsToTrue() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, null, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
    }

    @Test
    void createLeave_withNotifyUserNotFound_skipsNullUser() throws Exception {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, List.of(999L));

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(userProfileRepository.findById(999L)).thenReturn(Optional.empty());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
        verify(leaveNotifyUserRepository, never()).saveAll(any());
    }

    @Test
    void createLeave_notifyUserSameAsOwner_skipsOwner() throws Exception {

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, List.of(1L));

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
        verify(leaveNotifyUserRepository, never()).saveAll(any());
    }


    @Test
    void applyPartialStatus_managerMixed_removesRejectedAndSetsManagerApproved() throws Exception {
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        LeaveDate d2 = new LeaveDate(leaveRecord, LocalDate.now().plusDays(1), "FULL");
        setField(d1, "id", 1L);
        setField(d2, "id", 2L);

        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "manager1",
                List.of(
                        new PartialLeaveStatusRequest.DateDecision(LocalDate.now(), "FULL", "APPROVED"),
                        new PartialLeaveStatusRequest.DateDecision(LocalDate.now().plusDays(1), "FULL", "REJECTED")
                ),
                "One date rejected");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1, d2));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);
        assertThat(response).isNotNull();
        verify(leaveDateRepository).deleteAllById(any());
        verify(eventPublisher).publishManagerApproved(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void applyPartialStatus_managerMixed_withActiveFlowableTask_sendsManagerApprovedStatus() throws Exception {
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        LeaveDate d2 = new LeaveDate(leaveRecord, LocalDate.now().plusDays(1), "FULL");
        setField(d1, "id", 1L);
        setField(d2, "id", 2L);

        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "manager1",
                List.of(
                        new PartialLeaveStatusRequest.DateDecision(LocalDate.now(), "FULL", "APPROVED"),
                        new PartialLeaveStatusRequest.DateDecision(LocalDate.now().plusDays(1), "FULL", "REJECTED")
                ),
                "One date rejected");

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1, d2));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey("10")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("task_manager_approval")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-manager-1");

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);

        assertThat(response).isNotNull();
        verify(taskService).complete(eq("task-manager-1"), ArgumentMatchers.<Map<String, Object>>argThat(vars ->
                LeaveConstants.STATUS_MANAGER_APPROVED.equals(vars.get("status"))
                        && ((String) vars.get("customNote")).contains("Pending admin final approval")
        ));
    }

    @Test
    void applyPartialStatus_adminAllRejected_setsRejectedStatus() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        setField(d1, "id", 1L);

        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "admin1",
                List.of(new PartialLeaveStatusRequest.DateDecision(LocalDate.now(), "FULL", "REJECTED")),
                "Not approved by admin");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishPartialDecision(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("Not approved by admin"), anyDouble());
    }

    @Test
    void applyPartialStatus_adminMixed_deductsOnlyApprovedDays() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        LeaveDate d1 = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        LeaveDate d2 = new LeaveDate(leaveRecord, LocalDate.now().plusDays(1), "MORNING_HALF");
        setField(d1, "id", 1L);
        setField(d2, "id", 2L);

        PartialLeaveStatusRequest req = new PartialLeaveStatusRequest(
                "admin1",
                List.of(
                        new PartialLeaveStatusRequest.DateDecision(LocalDate.now(), "FULL", "APPROVED"),
                        new PartialLeaveStatusRequest.DateDecision(LocalDate.now().plusDays(1), "MORNING_HALF", "REJECTED")
                ),
                "One rejected");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(d1, d2));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);

        LeaveResponse response = leaveService.applyPartialStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishBalanceDeduct(any(), any(), eq(1.0), any());
        verify(eventPublisher).publishPartialDecision(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void updateLeaveStatus_managerApproved_withAdminEmails_sendsNotification() throws Exception {
        UserProfile admin = new UserProfile();
        setField(admin, "emailId", "admin@cresensolutions.com");

        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "MANAGER_APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(eventPublisher).publishManagerApproved(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateLeaveStatus_rejected_actorWithNullFullName_usesUsername() throws Exception {
        UserProfile actorUser = new UserProfile();
        setField(actorUser, "userName", "manager1");
        setField(actorUser, "fullName", null);
        setField(actorUser, "role", "MANAGER");

        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "REJECTED", "reason");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("manager1")).thenReturn(Optional.of(actorUser));

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
    }

    @Test
    void updateLeaveStatus_rejected_actorNotFound_usesUsernameAsFallback() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("ghost_user", "REJECTED", "reason");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("ghost_user")).thenReturn(Optional.empty());

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
    }

    @Test
    void updateLeaveStatus_rejected_actorWithNullRole_returnsEmptyRoleDisplay() throws Exception {
        UserProfile actorUser = new UserProfile();
        setField(actorUser, "userName", "manager1");
        setField(actorUser, "fullName", "Manager One");
        setField(actorUser, "role", null);

        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "REJECTED", "reason");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("manager1")).thenReturn(Optional.of(actorUser));

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
    }

    @Test
    void updateLeaveStatus_rejected_actorWithUnknownRole_usesCapitalized() throws Exception {
        UserProfile actorUser = new UserProfile();
        setField(actorUser, "userName", "contractor1");
        setField(actorUser, "fullName", "Contractor One");
        setField(actorUser, "role", "CONTRACTOR");

        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("contractor1", "REJECTED", "reason");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(userProfileRepository.findByUserName("contractor1")).thenReturn(Optional.of(actorUser));

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
    }


    @Test
    void createLeaveType_withMaleGender_normalizesCorrectly() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Paternity", "PATERNITY_LEAVE", "For fathers", 15, "male");

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenReturn(
                new LeaveType("Paternity", "PATERNITY_LEAVE", "For fathers", 15, "MALE"));

        LeaveTypeResponse response = leaveService.createLeaveType(req);
        assertThat(response).isNotNull();
    }

    @Test
    void createLeaveType_withInvalidGender_treatsAsNull() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Other Leave", "OTHER_LEAVE", null, 5, "UNKNOWN");

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenReturn(
                new LeaveType("Other Leave", "OTHER_LEAVE", null, 5, null));

        LeaveTypeResponse response = leaveService.createLeaveType(req);
        assertThat(response).isNotNull();
    }

    @Test
    void createLeaveType_withBlankDescription_savesNull() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Blank Desc", "BLANK_DESC", "   ", 5, null);

        when(leaveTypeRepository.findConflicts(any(), any())).thenReturn(List.of());
        when(leaveTypeRepository.save(any())).thenReturn(
                new LeaveType("Blank Desc", "BLANK_DESC", null, 5, null));

        LeaveTypeResponse response = leaveService.createLeaveType(req);
        assertThat(response).isNotNull();
    }


    @Test
    void getLeaveById_nullUser_returnsNullFullName() throws Exception {
        LeaveRecord noUserLeave = new LeaveRecord(null, leaveType, "reason", null, null, true);
        setField(noUserLeave, "id", 20L);

        when(leaveRepository.findDetailedById(20L)).thenReturn(Optional.of(noUserLeave));
        when(leaveDateRepository.findByApplicationId(20L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(20L)).thenReturn(List.of());

        LeaveResponse response = leaveService.getLeaveById(20L);
        assertThat(response.fullName()).isNull();
    }

    @Test
    void getLeaveById_managerApproved_hidesLegacyAdminApprovalFields() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        setField(leaveRecord, "approvedBy", "manager1");
        setField(leaveRecord, "adminApprovedBy", "manager1");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.getLeaveById(10L);

        assertThat(response.managerApprovedBy()).isEqualTo("manager1");
        assertThat(response.adminApprovedBy()).isNull();
    }

    @Test
    void getLeaveById_approved_usesLegacyApprovedByAsAdminFallback() throws Exception {
        setField(leaveRecord, "status", "APPROVED");
        setField(leaveRecord, "approvedBy", "admin1");
        setField(leaveRecord, "adminApprovedBy", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.getLeaveById(10L);

        assertThat(response.adminApprovedBy()).isEqualTo("admin1");
    }


    @Test
    void createLeave_withNotifyUserIds_includesInProcessVars() throws Exception {
        UserProfile notifyUser = new UserProfile();
        setField(notifyUser, "id", 5L);

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, List.of(5L));

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(userProfileRepository.findById(5L)).thenReturn(Optional.of(notifyUser));
        when(leaveNotifyUserRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
    }


    @Test
    void getLeavesByManagerUsername_withResults_returnsMappedPage() {
        Page<LeaveRecord> page = new PageImpl<>(List.of(leaveRecord));
        when(leaveRepository.findByManagerUsernamePaged(eq("manager1"), any())).thenReturn(page);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        Page<LeaveResponse> result = leaveService.getLeavesByManagerUsername("manager1", 0, 10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── getAuditTrail ─────────────────────────────────────────────────────────

    @Test
    void getAuditTrail_withValidTrail_returnsParsedEntries() throws Exception {
        String trail = "[{\"event\":\"SUBMITTED\",\"actor\":\"john\",\"timestamp\":\"2026-01-01T10:00:00+00:00\","
                + "\"processInstanceId\":\"proc-1\",\"taskId\":null,\"note\":\"Leave submitted\"}]";
        setField(leaveRecord, "trail", trail);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).event()).isEqualTo("SUBMITTED");
        assertThat(result.get(0).actor()).isEqualTo("john");
        assertThat(result.get(0).note()).isEqualTo("Leave submitted");
    }

    @Test
    void getAuditTrail_nullTrail_returnsEmptyList() throws Exception {
        setField(leaveRecord, "trail", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAuditTrail_emptyArrayTrail_returnsEmptyList() throws Exception {
        setField(leaveRecord, "trail", "[]");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAuditTrail_blankTrail_returnsEmptyList() throws Exception {
        setField(leaveRecord, "trail", "   ");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAuditTrail_invalidJson_returnsEmptyList() throws Exception {
        setField(leaveRecord, "trail", "not-valid-json");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAuditTrail_leaveNotFound_throwsResourceNotFound() {
        when(leaveRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.getAuditTrail(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave not found with id: 99");
    }

    @Test
    void getAuditTrail_multipleEntries_returnsAllParsed() throws Exception {
        String trail = "[{\"event\":\"SUBMITTED\",\"actor\":\"john\",\"timestamp\":\"2026-01-01T10:00:00+00:00\","
                + "\"processInstanceId\":null,\"taskId\":null,\"note\":\"Submitted\"},"
                + "{\"event\":\"MANAGER_APPROVED\",\"actor\":\"manager1\",\"timestamp\":\"2026-01-02T09:00:00+00:00\","
                + "\"processInstanceId\":\"proc-1\",\"taskId\":\"task-1\",\"note\":\"Approved by manager\"}]";
        setField(leaveRecord, "trail", trail);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).event()).isEqualTo("SUBMITTED");
        assertThat(result.get(1).event()).isEqualTo("MANAGER_APPROVED");
        assertThat(result.get(1).actor()).isEqualTo("manager1");
        assertThat(result.get(1).processInstanceId()).isEqualTo("proc-1");
        assertThat(result.get(1).taskId()).isEqualTo("task-1");
    }
}
