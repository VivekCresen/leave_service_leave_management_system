package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;

import java.util.List;

public interface LeaveService {

    LeaveResponse createLeave(CreateLeaveRequest request);

    LeaveResponse getLeaveById(Long leaveId);

    List<LeaveResponse> getAllLeaves();

    List<LeaveResponse> getLeavesByUserId(Long userId);

    List<LeaveTypeResponse> getLeaveTypes();
}
