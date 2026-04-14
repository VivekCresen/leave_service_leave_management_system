package com.cresensolutions.leaveservice.service;

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

    LeaveResponse updateLeave(Long leaveId, UpdateLeaveRequest request);

    void deletePendingLeave(Long leaveId);

    List<LeaveTypeResponse> getLeaveTypes();

    LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request);

    LeaveTypeResponse updateLeaveType(Integer leaveTypeId, CreateLeaveTypeRequest request);

    void deleteLeaveType(Integer leaveTypeId);

    List<NotifyUserResponse> getNotifyUsers(String username);

    void resolveApprover(DelegateExecution execution);

    void calculateReminderSchedule(DelegateExecution execution);

    void sendFourDayReminderEmail(DelegateExecution execution);

    void sendTwoDayReminderEmail(DelegateExecution execution);

    void updateApprovedLeaveStatus(DelegateExecution execution);

    void updateRejectedLeaveStatus(DelegateExecution execution);

    void deductLeaveBalance(DelegateExecution execution);

    void sendApprovedLeaveStatusMail(DelegateExecution execution);

    void sendRejectedLeaveStatusMail(DelegateExecution execution);
}
