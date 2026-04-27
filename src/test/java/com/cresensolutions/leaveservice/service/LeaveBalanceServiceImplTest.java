package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveBalanceServiceImplTest {

    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks private LeaveBalanceServiceImpl service;

    @Test
    void deductLeaveBalance_success_deductsCorrectly() {
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("ANNUAL_LEAVE");
        when(employeeLeaveRepository.deductLeaveBalance(1L, "ANNUAL_LEAVE", 2.0)).thenReturn(1);

        service.deductLeaveBalance(1L, 1, 2.0);

        verify(employeeLeaveRepository).deductLeaveBalance(1L, "ANNUAL_LEAVE", 2.0);
    }

    @Test
    void deductLeaveBalance_nullUserId_skips() {
        service.deductLeaveBalance(null, 1, 1.0);
        verifyNoInteractions(leaveTypeRepository, employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_nullLeaveTypeId_skips() {
        service.deductLeaveBalance(1L, null, 1.0);
        verifyNoInteractions(leaveTypeRepository, employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_nullLeaveUniqueName_skips() {
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn(null);

        service.deductLeaveBalance(1L, 1, 1.0);

        verify(leaveTypeRepository).findUniqueNameById(1);
        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_blankLeaveUniqueName_skips() {
        when(leaveTypeRepository.findUniqueNameById(1)).thenReturn("  ");

        service.deductLeaveBalance(1L, 1, 1.0);

        verify(leaveTypeRepository).findUniqueNameById(1);
        verifyNoInteractions(employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_halfDay_deductsHalfDay() {
        when(leaveTypeRepository.findUniqueNameById(2)).thenReturn("SICK_LEAVE");
        when(employeeLeaveRepository.deductLeaveBalance(1L, "SICK_LEAVE", 0.5)).thenReturn(1);

        service.deductLeaveBalance(1L, 2, 0.5);

        verify(employeeLeaveRepository).deductLeaveBalance(1L, "SICK_LEAVE", 0.5);
    }

    @Test
    void deductLeaveBalance_zeroDays_skips() {
        service.deductLeaveBalance(1L, 1, 0.0);
        verifyNoInteractions(leaveTypeRepository, employeeLeaveRepository);
    }

    @Test
    void deductLeaveBalance_negativeDays_skips() {
        service.deductLeaveBalance(1L, 1, -1.0);
        verifyNoInteractions(leaveTypeRepository, employeeLeaveRepository);
    }
}
