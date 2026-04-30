package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    @Query(value = """
            SELECT
                COALESCE(NULLIF(lt.leave_name, ''), lt.leave_unique_name) AS leave_name,
                lt.leave_unique_name AS leave_unique_name,
                CAST(balance.value AS DOUBLE PRECISION) AS remaining_balance
            FROM leave_schema.employee_leave el
            CROSS JOIN LATERAL jsonb_each_text(COALESCE(el.leaves, '{}'::jsonb)) balance(key, value)
            LEFT JOIN leave_schema.leave_types lt ON LOWER(lt.leave_unique_name) = LOWER(balance.key)
            WHERE el.user_id = :userId
            ORDER BY COALESCE(lt.id, 999999), leave_name
            """, nativeQuery = true)
    List<Object[]> findLeaveBalancesByUserId(@Param("userId") Long userId);
}
