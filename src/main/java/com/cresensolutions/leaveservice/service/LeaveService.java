package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.NotifyUserResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveRequest;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
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

    /**
     * Returns users that can be selected as notify recipients.
     * For an employee: returns their manager's team members.
     * For a manager: returns their own team members.
     */
    List<NotifyUserResponse> getNotifyUsers(String username);
}
