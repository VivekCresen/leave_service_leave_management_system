package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
import org.springframework.data.domain.Page;

import java.util.List;

public interface LeaveService {

    LeaveResponse createLeave(CreateLeaveRequest request);

    LeaveResponse getLeaveById(Long leaveId);

    Page<LeaveResponse> getAllLeaves(int page, int size);

    Page<LeaveResponse> getLeavesByUserId(Long userId, int page, int size);

    LeaveResponse updateLeaveStatus(Long leaveId, UpdateLeaveStatusRequest request);

    List<LeaveTypeResponse> getLeaveTypes();

    LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request);

    LeaveTypeResponse updateLeaveType(Integer leaveTypeId, CreateLeaveTypeRequest request);

    void deleteLeaveType(Integer leaveTypeId);
}
