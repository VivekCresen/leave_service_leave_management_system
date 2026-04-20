package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.PartialLeaveStatusRequest;
import com.cresensolutions.leaveservice.dto.AppendAuditTrailRequest;
import com.cresensolutions.leaveservice.dto.AuditTrailEntryDto;
import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.NotifyUserResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveRequest;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.data.domain.Page;

import java.util.List;

public interface LeaveService {

    LeaveResponse createLeave(CreateLeaveRequest request);

    LeaveResponse getLeaveById(Long leaveId);

    Page<LeaveResponse> getAllLeaves(int page, int size);

    Page<LeaveResponse> getLeavesByUserId(Long userId, int page, int size);

    Page<LeaveResponse> getLeavesByUsername(String username, int page, int size);

    Page<LeaveResponse> getLeavesByManagerUsername(String managerUsername, int page, int size);

    LeaveResponse updateLeaveStatus(Long leaveId, UpdateLeaveStatusRequest request);

    LeaveResponse applyPartialStatus(Long leaveId, PartialLeaveStatusRequest request);

    LeaveResponse updateLeave(Long leaveId, UpdateLeaveRequest request);

    void deletePendingLeave(Long leaveId);

    List<LeaveTypeResponse> getLeaveTypes();

    LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request);

    LeaveTypeResponse updateLeaveType(Integer leaveTypeId, CreateLeaveTypeRequest request);

    void deleteLeaveType(Integer leaveTypeId);

    List<NotifyUserResponse> getNotifyUsers(String username);

    List<String> getBookedDates(String username);

    List<AuditTrailEntryDto> getAuditTrail(Long leaveId);

    LeaveResponse appendAuditTrailEntry(Long leaveId, AppendAuditTrailRequest request);

    void resolveApprover(DelegateExecution execution);

    void calculateReminderSchedule(DelegateExecution execution);

    void sendReminderEmail(DelegateExecution execution);

    void updateLeaveStatusFromFlowable(DelegateExecution execution);

    void notifyAdminForFinalApproval(DelegateExecution execution);

    void deductLeaveBalance(DelegateExecution execution);

    void sendLeaveStatusMail(DelegateExecution execution);
}
