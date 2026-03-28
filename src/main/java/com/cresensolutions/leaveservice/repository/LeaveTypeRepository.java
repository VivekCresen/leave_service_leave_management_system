package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {

    List<LeaveType> findAllByOrderByIdAsc();
}
