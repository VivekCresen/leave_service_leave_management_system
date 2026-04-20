package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class LeaveBalanceServiceImpl implements LeaveBalanceService {

    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveBalanceServiceImpl(
            EmployeeLeaveRepository employeeLeaveRepository,
            LeaveTypeRepository leaveTypeRepository
    ) {
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Override
    @Transactional
    public void deductLeaveBalance(Long userId, Integer leaveTypeId, double days) {
        if (userId == null || leaveTypeId == null) return;
        String leaveUniqueName = leaveTypeRepository.findUniqueNameById(leaveTypeId);
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) {
            log.warn("[LeaveBalanceService] No leaveUniqueName for leaveTypeId={}, skipping.", leaveTypeId);
            return;
        }
        int rows = employeeLeaveRepository.deductLeaveBalance(userId, leaveUniqueName, days);
        log.info("[LeaveBalanceService] Deducted {} days of '{}' for userId={} rows={}",
                days, leaveUniqueName, userId, rows);
    }
}
