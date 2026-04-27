package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeaveBalanceServiceImpl implements LeaveBalanceService {

    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveBalanceServiceImpl(EmployeeLeaveRepository employeeLeaveRepository,
                                   LeaveTypeRepository leaveTypeRepository) {
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Override
    public void deductLeaveBalance(Long userId, Integer leaveTypeId, Double days) {
        if (userId == null || leaveTypeId == null || days == null || days <= 0) {
            return;
        }

        String leaveUniqueName = leaveTypeRepository.findUniqueNameById(leaveTypeId);
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) {
            return;
        }

        employeeLeaveRepository.deductLeaveBalance(userId, leaveUniqueName, days);
    }
}
