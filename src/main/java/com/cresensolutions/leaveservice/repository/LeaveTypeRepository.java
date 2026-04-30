package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {

    @Query(value = "SELECT * FROM leave_schema.leave_types ORDER BY id ASC", nativeQuery = true)
    List<LeaveType> findAllOrderedById();

    @Query(value = """
            SELECT * FROM leave_schema.leave_types
            WHERE LOWER(leave_name) = LOWER(:leaveName)
               OR LOWER(leave_unique_name) = LOWER(:leaveUniqueName)
            """, nativeQuery = true)
    List<LeaveType> findConflicts(@Param("leaveName") String leaveName,
                                  @Param("leaveUniqueName") String leaveUniqueName);

    @Query(value = """
            SELECT * FROM leave_schema.leave_types
            WHERE id <> :leaveTypeId
              AND (LOWER(leave_name) = LOWER(:leaveName)
                OR LOWER(leave_unique_name) = LOWER(:leaveUniqueName))
            """, nativeQuery = true)
    List<LeaveType> findConflictsForUpdate(@Param("leaveTypeId") Integer leaveTypeId,
                                            @Param("leaveName") String leaveName,
                                            @Param("leaveUniqueName") String leaveUniqueName);

    @Query(value = "SELECT leave_unique_name FROM leave_schema.leave_types WHERE id = :id", nativeQuery = true)
    String findUniqueNameById(@Param("id") Integer id);

    @Query(value = "SELECT leave_name, max_days, description FROM leave_schema.leave_types ORDER BY leave_name ASC", nativeQuery = true)
    List<Object[]> findAllForChatbot();
}
