package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveDateRepository extends JpaRepository<LeaveDate, Long> {

    @Query("SELECT ld FROM LeaveDate ld WHERE ld.leaveApplication.id = :applicationId ORDER BY ld.leaveDate ASC")
    List<LeaveDate> findByApplicationId(@Param("applicationId") Long applicationId);

    @Modifying
    @Query("DELETE FROM LeaveDate ld WHERE ld.leaveApplication.id = :applicationId")
    void deleteByApplicationId(@Param("applicationId") Long applicationId);
}
