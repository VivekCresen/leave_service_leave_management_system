package com.cresensolutions.leaveservice.service;

public interface LeaveBalanceService {

    void deductLeaveBalance(Long userId, Integer leaveTypeId, Double days);
}
