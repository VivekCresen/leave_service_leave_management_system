package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveNotifyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveNotifyUserRepository extends JpaRepository<LeaveNotifyUser, Long> {

    @Query("SELECT n FROM LeaveNotifyUser n WHERE n.leave.id = :leaveId")
    List<LeaveNotifyUser> findByLeaveId(@Param("leaveId") Long leaveId);

    @Modifying
    void deleteByLeaveId(Long leaveId);

    @Modifying
    @Query("DELETE FROM LeaveNotifyUser n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
