package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.dto.*;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.messaging.LeaveEventPublisher;
import com.cresensolutions.leaveservice.model.*;
import com.cresensolutions.leaveservice.repository.*;
import com.cresensolutions.leaveservice.service.Impl.LeaveServiceImpl;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceImplFlowableTest {

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
    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private LeaveEventPublisher eventPublisher;

    @InjectMocks private LeaveServiceImpl leaveService;

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
    @SuppressWarnings("unchecked")
    void updateLeaveStatus_withPendingFlowableTask_completesTask() throws Exception {
        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("admin1", "APPROVED", null);
        Task mockTask = mock(Task.class);
        when(mockTask.getId()).thenReturn("task-123");

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(mockTask);
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);
        assertThat(response).isNotNull();
        verify(taskService).complete(eq("task-123"), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLeaveStatus_withRejectionAndFlowableTask_passesRejectionReason() {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "REJECTED", "Not enough notice");
        Task mockTask = mock(Task.class);
        when(mockTask.getId()).thenReturn("task-456");

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(mockTask);
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        leaveService.updateLeaveStatus(10L, req);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass((Class) Map.class);
        verify(taskService).complete(eq("task-456"), captor.capture());
        assertThat(captor.getValue()).containsEntry("rejectionReason", "Not enough notice");
    }

    @Test
    void updateLeaveStatus_flowableTaskQueryThrows_fallsBackToDirectUpdate() throws Exception {

        setField(leaveRecord, "status", "MANAGER_APPROVED");
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("admin1", "APPROVED", null);

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(taskService.createTaskQuery()).thenThrow(new RuntimeException("Flowable unavailable"));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.updateLeaveStatus(10L, req);

        assertThat(response).isNotNull();
        verify(leaveRepository).save(any(LeaveRecord.class));
    }


    @Test
    void resolveApprover_withUserId_resolvesManagerAndSetsVariables() {
        UserProfile manager = new UserProfile();
        setFieldSafe(manager, "id", 2L);
        setFieldSafe(manager, "userName", "manager1");
        setFieldSafe(manager, "emailId", "manager@cresensolutions.com");
        setFieldSafe(manager, "active", true);

        UserProfile admin = new UserProfile();
        setFieldSafe(admin, "id", 3L);
        setFieldSafe(admin, "emailId", "admin@cresensolutions.com");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(manager));
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        leaveService.resolveApprover(execution);

        verify(execution).setVariable("managerUsername", "manager1");
        verify(execution).setVariable("managerEmail", "manager@cresensolutions.com");
        verify(execution).setVariable("adminEmail", "admin@cresensolutions.com");
        verify(execution).setVariable("employeeName", "John Doe");
    }

    @Test
    void resolveApprover_noManagerFound_fallsBackToAdmin() {
        UserProfile admin = new UserProfile();
        setFieldSafe(admin, "id", 3L);
        setFieldSafe(admin, "userName", "admin");
        setFieldSafe(admin, "emailId", "admin@cresensolutions.com");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.empty());
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));

        leaveService.resolveApprover(execution);

        verify(execution).setVariable(eq("managerUsername"), eq("admin"));
    }

    @Test
    void resolveApprover_userNotFound_setsDefaultVariables() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(99L);
        when(execution.getVariable("username")).thenReturn("ghost");
        when(execution.getVariable("leaveId")).thenReturn(null);

        when(userProfileRepository.findById(99L)).thenReturn(Optional.empty());

        leaveService.resolveApprover(execution);

        verify(execution).setVariable("managerUsername", "admin");
        verify(execution).setVariable("managerEmail", "");
        verify(execution).setVariable("adminEmail", "");
        verify(execution).setVariable("employeeName", "ghost");
    }

    @Test
    void resolveApprover_nullUserIdAndUsername_setsDefaultVariables() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(null);
        when(execution.getVariable("username")).thenReturn(null);
        when(execution.getVariable("leaveId")).thenReturn(null);

        leaveService.resolveApprover(execution);

        verify(execution).setVariable("managerUsername", "admin");
        verify(execution).setVariable("employeeName", LeaveConstants.DEFAULT_EMPLOYEE_NAME);
    }

    @Test
    void resolveApprover_withLeaveIdAndRecipients_sendsApprovalEmail() {
        UserProfile manager = new UserProfile();
        setFieldSafe(manager, "id", 2L);
        setFieldSafe(manager, "userName", "manager1");
        setFieldSafe(manager, "emailId", "manager@cresensolutions.com");
        setFieldSafe(manager, "active", true);

        UserProfile admin = new UserProfile();
        setFieldSafe(admin, "id", 3L);
        setFieldSafe(admin, "emailId", "admin@cresensolutions.com");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(manager));
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        leaveService.resolveApprover(execution);

        verify(eventPublisher).publishLeaveReminder(
                eq(10L), eq("PENDING_APPROVAL"),
                eq("manager@cresensolutions.com"), eq("admin@cresensolutions.com"),
                eq("John Doe"), eq("Annual Leave"), eq("Vacation"),
                eq("proc-1"), isNull());
        verify(leaveRepository).save(leaveRecord);
    }

    @Test
    void resolveApprover_withManagerProfile_usesManagerDisplayNameAndRole() {
        UserProfile manager = new UserProfile();
        setFieldSafe(manager, "id", 2L);
        setFieldSafe(manager, "userName", "manager1");
        setFieldSafe(manager, "fullName", "Manager One");
        setFieldSafe(manager, "emailId", "manager@cresensolutions.com");
        setFieldSafe(manager, "active", true);
        setFieldSafe(manager, "role", "MANAGER");

        UserProfile admin = new UserProfile();
        setFieldSafe(admin, "id", 3L);
        setFieldSafe(admin, "userName", "admin1");
        setFieldSafe(admin, "emailId", "admin@cresensolutions.com");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(manager));
        when(userProfileRepository.findByUserName("manager1")).thenReturn(Optional.of(manager));
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        leaveService.resolveApprover(execution);

        verify(eventPublisher).publishLeaveReminder(
                eq(10L), eq("PENDING_APPROVAL"),
                eq("manager@cresensolutions.com"), eq("admin@cresensolutions.com"),
                eq("John Doe"), eq("Annual Leave"), eq("Vacation"),
                eq("proc-1"), isNull());
    }

    @Test
    void resolveApprover_managerInactive_fallsBackToAdmin() {
        UserProfile inactiveManager = new UserProfile();
        setFieldSafe(inactiveManager, "id", 2L);
        setFieldSafe(inactiveManager, "userName", "manager1");
        setFieldSafe(inactiveManager, "emailId", "manager@cresensolutions.com");
        setFieldSafe(inactiveManager, "active", false);

        UserProfile admin = new UserProfile();
        setFieldSafe(admin, "id", 3L);
        setFieldSafe(admin, "userName", "admin");
        setFieldSafe(admin, "emailId", "admin@cresensolutions.com");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findByUserNameIgnoreCase("manager1")).thenReturn(Optional.of(inactiveManager));
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));

        leaveService.resolveApprover(execution);

        verify(execution).setVariable(eq("managerUsername"), eq("admin"));
    }


    @Test
    void calculateReminderSchedule_withFutureDates_setsTimers() {
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
        String json = "[{\"date\":\"" + LocalDate.now().minusDays(10) + "\",\"dayType\":\"FULL\"}]";
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
    void deductLeaveBalance_success_deductsCorrectDays() {
        LeaveDate fullDay = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        LeaveDate halfDay = new LeaveDate(leaveRecord, LocalDate.now().plusDays(1), "MORNING_HALF");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("leaveTypeId")).thenReturn(1);
        when(execution.getVariable("leaveId")).thenReturn(10L);

        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("ANNUAL_LEAVE");
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(fullDay, halfDay));
        when(employeeLeaveRepository.deductLeaveBalance(1L, "ANNUAL_LEAVE", 1.5)).thenReturn(1);

        leaveService.deductLeaveBalance(execution);

        verify(employeeLeaveRepository).deductLeaveBalance(1L, "ANNUAL_LEAVE", 1.5);
    }

    @Test
    void deductLeaveBalance_nullUserId_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(null);
        when(execution.getVariable("leaveTypeId")).thenReturn(1);

        leaveService.deductLeaveBalance(execution);

        verifyNoInteractions(leaveTypeRepository);
        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_nullLeaveTypeId_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("leaveTypeId")).thenReturn(null);

        leaveService.deductLeaveBalance(execution);

        verifyNoInteractions(leaveTypeRepository);
        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_blankLeaveUniqueName_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("leaveTypeId")).thenReturn(1);
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("");

        leaveService.deductLeaveBalance(execution);

        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_nullLeaveId_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("leaveTypeId")).thenReturn(1);
        when(execution.getVariable("leaveId")).thenReturn(null);
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("ANNUAL_LEAVE");

        leaveService.deductLeaveBalance(execution);

        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_zeroDays_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("leaveTypeId")).thenReturn(1);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("ANNUAL_LEAVE");
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());

        leaveService.deductLeaveBalance(execution);

        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_leaveTypeIdAsString_parsesCorrectly() {
        LeaveDate fullDay = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(execution.getVariable("leaveTypeId")).thenReturn("1");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("ANNUAL_LEAVE");
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(fullDay));
        when(employeeLeaveRepository.deductLeaveBalance(1L, "ANNUAL_LEAVE", 1.0)).thenReturn(1);

        leaveService.deductLeaveBalance(execution);

        verify(employeeLeaveRepository).deductLeaveBalance(1L, "ANNUAL_LEAVE", 1.0);
    }



    @Test
    void updateLeaveStatusFromFlowable_missingStatus_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn(null);

        leaveService.updateLeaveStatusFromFlowable(execution);

        verifyNoInteractions(leaveRepository);
    }

    @Test
    void updateLeaveStatusFromFlowable_blankStatus_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn("  ");

        leaveService.updateLeaveStatusFromFlowable(execution);

        verifyNoInteractions(leaveRepository);
    }

    @Test
    void updateLeaveStatusFromFlowable_missingLeaveId_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("leaveId")).thenReturn(null);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        leaveService.updateLeaveStatusFromFlowable(execution);

        verifyNoInteractions(leaveRepository);
    }

    @Test
    void updateLeaveStatusFromFlowable_approved_setsVariables() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("task-1");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);

        leaveService.updateLeaveStatusFromFlowable(execution);

        verify(execution).setVariable("employeeName", "John Doe");
        verify(execution).setVariable("leaveType", "ANNUAL_LEAVE");
    }

    @Test
    void updateLeaveStatusFromFlowable_rejected_appendsRejectionNote() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("status")).thenReturn("REJECTED");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn("Not enough notice");
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("task-1");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);

        leaveService.updateLeaveStatusFromFlowable(execution);

        assertThat(leaveRecord.getStatus()).isEqualTo("REJECTED");
        verify(leaveRepository).save(leaveRecord);
    }

    @Test
    void sendReminderEmail_dispatches() {
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
        when(execution.getCurrentActivityId()).thenReturn("task-1");

        leaveService.sendReminderEmail(execution);

        verify(eventPublisher).publishLeaveReminder(
                eq(10L), eq("4DAY"),
                eq("manager@cresensolutions.com"), eq("admin@cresensolutions.com"),
                eq("john"), eq("Annual Leave"), eq("Vacation"),
                eq("proc-1"), eq("task-1"));
    }

    @Test
    void sendReminderEmail_nullReminderType_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("reminderType")).thenReturn(null);

        leaveService.sendReminderEmail(execution);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendReminderEmail_blankReminderType_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("reminderType")).thenReturn("  ");

        leaveService.sendReminderEmail(execution);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendReminderEmail_nullManagerAndAdminEmail_usesEmpty() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("reminderType")).thenReturn("2DAY");
        when(execution.getVariable("managerEmail")).thenReturn(null);
        when(execution.getVariable("adminEmail")).thenReturn(null);
        when(execution.getVariable("employeeName")).thenReturn("John");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn(null);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("task-1");

        leaveService.sendReminderEmail(execution);

        verify(eventPublisher).publishLeaveReminder(
                eq(10L), eq("2DAY"), eq(""), eq(""), eq("John"), any(), eq(""), any(), any());
    }

    @Test
    void sendReminderEmail_blankEmployeeName_fallsBackToUsername() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("reminderType")).thenReturn("2DAY");
        when(execution.getVariable("managerEmail")).thenReturn("mgr@test.com");
        when(execution.getVariable("adminEmail")).thenReturn(null);
        when(execution.getVariable("employeeName")).thenReturn("  ");
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("task-1");

        leaveService.sendReminderEmail(execution);

        verify(eventPublisher).publishLeaveReminder(
                anyLong(), any(), any(), any(), eq("john"), any(), any(), any(), any());
    }


    @Test
    void sendLeaveStatusMail_sendsNotification() {
        LeaveDate ld = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn("John Doe");
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(ld));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        leaveService.sendLeaveStatusMail(execution);

        verify(eventPublisher).publishLeaveApproved(
                eq(10L), eq(1L), eq("john"), eq("john@cresensolutions.com"),
                eq("Annual Leave"), eq(1), any(), eq("manager1"), eq("Manager"), anyDouble());
    }

    @Test
    void sendLeaveStatusMail_includesTrimmedNotifyRecipients() {
        LeaveDate ld = new LeaveDate(leaveRecord, LocalDate.now(), "FULL");
        LeaveNotifyUser notifyUser = mock(LeaveNotifyUser.class);
        when(notifyUser.getUserEmail()).thenReturn("  jane@cresensolutions.com  ");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn("John Doe");
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of(ld));
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of(notifyUser));

        leaveService.sendLeaveStatusMail(execution);

        verify(eventPublisher).publishLeaveApproved(
                eq(10L), eq(1L), eq("john"), eq("john@cresensolutions.com"),
                eq("Annual Leave"), eq(1), any(), eq("manager1"), eq("Manager"), anyDouble());
    }

    @Test
    void sendLeaveStatusMail_noRecipients_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn("John");
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.empty());

        leaveService.sendLeaveStatusMail(execution);

        verifyNoInteractions(leaveEmailService);
    }

    @Test
    void sendLeaveStatusMail_nullLeaveId_skips() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(null);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn("John");
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        leaveService.sendLeaveStatusMail(execution);

        verifyNoInteractions(leaveEmailService);
    }

    @Test
    void sendLeaveStatusMail_blankEmployeeName_usesDefault() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("leaveId")).thenReturn(10L);
        when(execution.getVariable("status")).thenReturn("APPROVED");
        when(execution.getVariable("employeeName")).thenReturn(null);
        when(execution.getVariable("leaveType")).thenReturn("Annual Leave");
        when(execution.getVariable("reason")).thenReturn("Vacation");
        when(execution.getVariable("actorUsername")).thenReturn("manager1");
        when(execution.getVariable("rejectionReason")).thenReturn(null);

        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        leaveService.sendLeaveStatusMail(execution);

        verify(eventPublisher).publishLeaveApproved(
                eq(10L), eq(1L), eq("john"), eq("john@cresensolutions.com"),
                eq("Annual Leave"), eq(1), any(), eq("manager1"), eq("Manager"), anyDouble());
    }


    @Test
    void getAuditTrail_withValidTrail_returnsParsedEntries() throws Exception {
        String trail = "[{\"event\":\"SUBMITTED\",\"actor\":\"john\",\"timestamp\":\"2026-01-01T00:00:00Z\"," +
                "\"processInstanceId\":\"proc-1\",\"taskId\":\"task-1\",\"note\":\"submitted\"}]";
        setField(leaveRecord, "trail", trail);
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        List<AuditTrailEntryDto> result = leaveService.getAuditTrail(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).event()).isEqualTo("SUBMITTED");
        assertThat(result.get(0).actor()).isEqualTo("john");
    }

    @Test
    void getAuditTrail_emptyTrail_returnsEmpty() throws Exception {
        setField(leaveRecord, "trail", "[]");
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThat(leaveService.getAuditTrail(10L)).isEmpty();
    }

    @Test
    void getAuditTrail_nullTrail_returnsEmpty() throws Exception {
        setField(leaveRecord, "trail", null);
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThat(leaveService.getAuditTrail(10L)).isEmpty();
    }

    @Test
    void getAuditTrail_invalidJson_returnsEmpty() throws Exception {
        setField(leaveRecord, "trail", "not-valid-json");
        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));

        assertThat(leaveService.getAuditTrail(10L)).isEmpty();
    }

    @Test
    void getAuditTrail_leaveNotFound_throwsResourceNotFound() {
        when(leaveRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.getAuditTrail(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    @Test
    void appendAuditTrailEntry_success() {
        AppendAuditTrailRequest req = new AppendAuditTrailRequest(
                "MANUAL_NOTE", "admin", "proc-1", "task-1", "note");

        when(leaveRepository.findDetailedById(10L)).thenReturn(Optional.of(leaveRecord));
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());

        LeaveResponse response = leaveService.appendAuditTrailEntry(10L, req);

        assertThat(response).isNotNull();
        verify(leaveRepository).save(leaveRecord);
    }

    @Test
    void appendAuditTrailEntry_leaveNotFound_throws() {
        AppendAuditTrailRequest req = new AppendAuditTrailRequest("EVENT", "actor", null, null, null);
        when(leaveRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.appendAuditTrailEntry(99L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    @Test
    void getBookedDates_returnsDateStrings() {
        when(leaveRepository.findBookedDatesByUsername("john"))
                .thenReturn(List.of("2026-05-01", "2026-05-02"));

        assertThat(leaveService.getBookedDates("john"))
                .containsExactly("2026-05-01", "2026-05-02");
    }

    @Test
    void getBookedDates_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> leaveService.getBookedDates(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }


    @Test
    void createLeave_startsFlowableProcess() {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, null);

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc-123");

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(employeeLeaveRepository.getRemainingBalance(1L, "ANNUAL_LEAVE")).thenReturn(10.0);
        when(leaveRepository.save(any())).thenReturn(leaveRecord);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(10L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(10L)).thenReturn(List.of());
        when(runtimeService.startProcessInstanceByKey(any(), any(), any())).thenReturn(pi);

        LeaveResponse response = leaveService.createLeave(req);

        assertThat(response).isNotNull();
        verify(runtimeService).startProcessInstanceByKey(
                eq(LeaveConstants.PROCESS_DEF_KEY), any(), any());
    }

    @Test
    void createLeave_flowableThrows_doesNotPropagate() {
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
        when(runtimeService.startProcessInstanceByKey(any(), any(), any()))
                .thenThrow(new RuntimeException("Flowable error"));

        assertThatNoException().isThrownBy(() -> leaveService.createLeave(req));
    }


    @Test
    void resolveApprover_withUsernameOnly_resolvesUserByUsername() {
        UserProfile admin = new UserProfile();
        setFieldSafe(admin, "emailId", "admin@cresensolutions.com");
        setFieldSafe(admin, "userName", "admin");

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("userId")).thenReturn(null);
        when(execution.getVariable("username")).thenReturn("john");
        when(execution.getVariable("leaveId")).thenReturn(null);

        when(userProfileRepository.findByUserName("john")).thenReturn(Optional.of(activeUser));
        when(userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN)).thenReturn(List.of(admin));

        leaveService.resolveApprover(execution);

        verify(execution).setVariable(eq("employeeName"), any());
    }

    @Test
    void createLeave_femaleUserFemaleOnlyLeave_succeeds() throws Exception {
        LeaveType femaleOnly = new LeaveType("Maternity", "MATERNITY", "desc", 90, "FEMALE");
        setField(femaleOnly, "id", 5);

        UserProfile femaleUser = new UserProfile();
        setField(femaleUser, "id", 2L);
        setField(femaleUser, "userName", "jane");
        setField(femaleUser, "fullName", "Jane Doe");
        setField(femaleUser, "emailId", "jane@cresensolutions.com");
        setField(femaleUser, "active", true);
        setField(femaleUser, "gender", "FEMALE");
        setField(femaleUser, "role", "EMPLOYEE");
        setField(femaleUser, "createdBy", "manager1");

        LeaveRecord femaleLeave = new LeaveRecord(femaleUser, femaleOnly, "Maternity", null, null, true);
        setField(femaleLeave, "id", 20L);

        CreateLeaveRequest req = new CreateLeaveRequest(
                2L, null, 5,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Maternity", null, null, true, null);

        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(femaleUser));
        when(leaveTypeRepository.findById(5)).thenReturn(Optional.of(femaleOnly));
        when(employeeLeaveRepository.getRemainingBalance(2L, "MATERNITY")).thenReturn(90.0);
        when(leaveRepository.save(any())).thenReturn(femaleLeave);
        when(leaveDateRepository.saveAll(any())).thenReturn(List.of());
        when(leaveDateRepository.findByApplicationId(20L)).thenReturn(List.of());
        when(leaveNotifyUserRepository.findByLeaveId(20L)).thenReturn(List.of());

        LeaveResponse response = leaveService.createLeave(req);
        assertThat(response).isNotNull();
    }

    @Test
    void createLeave_maleUserFemaleOnlyLeave_throws() throws Exception {
        LeaveType femaleOnly = new LeaveType("Maternity", "MATERNITY", "desc", 90, "FEMALE");
        setField(femaleOnly, "id", 5);

        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 5,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Maternity", null, null, true, null);

        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(leaveTypeRepository.findById(5)).thenReturn(Optional.of(femaleOnly));

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Female");
    }

    @Test
    void createLeave_femaleUserMaleOnlyLeave_throws() throws Exception {
        LeaveType maleOnly = new LeaveType("Paternity", "PATERNITY", "desc", 15, "MALE");
        setField(maleOnly, "id", 6);

        UserProfile femaleUser = new UserProfile();
        setField(femaleUser, "id", 3L);
        setField(femaleUser, "active", true);
        setField(femaleUser, "gender", "FEMALE");

        CreateLeaveRequest req = new CreateLeaveRequest(
                3L, null, 6,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Paternity", null, null, true, null);

        when(userProfileRepository.findById(3L)).thenReturn(Optional.of(femaleUser));
        when(leaveTypeRepository.findById(6)).thenReturn(Optional.of(maleOnly));

        assertThatThrownBy(() -> leaveService.createLeave(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Male");
    }


    @Test
    void createLeaveType_blankLeaveName_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "  ", "SOME_LEAVE", null, 5, null);

        assertThatThrownBy(() -> leaveService.createLeaveType(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leave name is required");
    }


    @Test
    void updateLeaveType_nullMaxDays_throwsIllegalArgument() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Annual Leave", "ANNUAL_LEAVE", null, null, null);

        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));

        assertThatThrownBy(() -> leaveService.updateLeaveType(1, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max days is required");
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

    private static void setFieldSafe(Object target, String fieldName, Object value) {
        try {
            setField(target, fieldName, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
