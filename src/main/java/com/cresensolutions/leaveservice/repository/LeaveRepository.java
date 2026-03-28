package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRepository extends JpaRepository<LeaveRecord, Long> {

    List<LeaveRecord> findAllByOrderByFromDateDescIdDesc();

    List<LeaveRecord> findAllByUserIdOrderByFromDateDescIdDesc(Long userId);
}
