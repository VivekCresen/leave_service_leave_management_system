package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {
}
