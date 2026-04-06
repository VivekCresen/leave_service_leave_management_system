package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {

    @Query(value = "SELECT * FROM employee_leave WHERE user_id = :userId LIMIT 1", nativeQuery = true)
    Optional<EmployeeLeave> findByUserId(@Param("userId") Long userId);
   
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE employee_leave
            SET leaves = jsonb_set(
                leaves::jsonb,
                ARRAY[:leaveKey],
                to_jsonb(GREATEST(0, COALESCE((leaves::jsonb->>:leaveKey)::int, 0) - :days))
            )::text
            WHERE user_id = :userId
            """, nativeQuery = true)
    int deductLeaveBalance(@Param("userId") Long userId,
                           @Param("leaveKey") String leaveKey,
                           @Param("days") int days);

    @Query(value = """
            SELECT COALESCE((leaves::jsonb->>:leaveKey)::int, 0)
            FROM employee_leave
            WHERE user_id = :userId
            """, nativeQuery = true)
    Integer getRemainingBalance(@Param("userId") Long userId,
                                @Param("leaveKey") String leaveKey);
}
