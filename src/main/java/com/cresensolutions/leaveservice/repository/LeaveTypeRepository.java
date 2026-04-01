package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {

    List<LeaveType> findAllByOrderByIdAsc();

    boolean existsByLeaveNameIgnoreCase(String leaveName);

    boolean existsByLeaveUniqueNameIgnoreCase(String leaveUniqueName);

    boolean existsByLeaveNameIgnoreCaseAndIdNot(String leaveName, Integer id);

    boolean existsByLeaveUniqueNameIgnoreCaseAndIdNot(String leaveUniqueName, Integer id);
}
