package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {

    @Query(value = """
            SELECT CAST(leaves ->> :leaveUniqueName AS DOUBLE PRECISION)
            FROM leave_schema.employee_leave
            WHERE user_id = :userId
            """, nativeQuery = true)
    Double getRemainingBalance(@Param("userId") Long userId,
                               @Param("leaveUniqueName") String leaveUniqueName);

    @Modifying
    @Query(value = """
            UPDATE leave_schema.employee_leave
            SET leaves = jsonb_set(
                COALESCE(leaves, '{}'::jsonb),
                ARRAY[:leaveUniqueName],
                to_jsonb(GREATEST(
                    COALESCE(CAST(leaves ->> :leaveUniqueName AS DOUBLE PRECISION), 0) - :days,
                    0
                )),
                true
            )
            WHERE user_id = :userId
            """, nativeQuery = true)
    int deductLeaveBalance(@Param("userId") Long userId,
                           @Param("leaveUniqueName") String leaveUniqueName,
                           @Param("days") Double days);
}
