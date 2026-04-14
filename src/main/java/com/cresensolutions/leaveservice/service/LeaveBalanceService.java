package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveBalanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveBalanceService.class);

    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveBalanceService(EmployeeLeaveRepository employeeLeaveRepository,
                               LeaveTypeRepository leaveTypeRepository) {
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Transactional
    public void deductLeaveBalance(Long userId, Integer leaveTypeId, double days) {
        if (userId == null || leaveTypeId == null) return;
        String leaveUniqueName = leaveTypeRepository.findUniqueNameById(leaveTypeId);
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) {
            LOGGER.warn("[LeaveBalanceService] No leaveUniqueName for leaveTypeId={}, skipping.", leaveTypeId);
            return;
        }
        int rows = employeeLeaveRepository.deductLeaveBalance(userId, leaveUniqueName, days);
        LOGGER.info("[LeaveBalanceService] Deducted {} days of '{}' for userId={} rows={}",
                days, leaveUniqueName, userId, rows);
    }
}
