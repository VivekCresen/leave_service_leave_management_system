package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRepository extends JpaRepository<LeaveRecord, Long> {

    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            LEFT JOIN user_schema.user_profile u ON l.user_id = u.id
            LEFT JOIN leave_schema.leave_types lt ON l.leave_type_id = lt.id
            WHERE l.id = :id
            """, nativeQuery = true)
    Optional<LeaveRecord> findDetailedById(@Param("id") Long id);

    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            WHERE UPPER(COALESCE(l.status, 'PENDING')) = 'PENDING'
            ORDER BY l.created_at ASC, l.id ASC
            """, nativeQuery = true)
    List<LeaveRecord> findPendingLeavesForReminder();

    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            WHERE UPPER(COALESCE(l.status, 'PENDING')) = 'PENDING'
              AND EXISTS (
                SELECT 1 FROM leave_schema.leave_dates ld
                WHERE ld.leave_application_id = l.id
                  AND ld.leave_date >= CURRENT_DATE
              )
            ORDER BY l.created_at ASC, l.id ASC
            """, nativeQuery = true)
    List<LeaveRecord> findPendingLeavesWithFutureDates();

    @Query(value = "SELECT l.* FROM leave_schema.leave_application l ORDER BY l.created_at DESC, l.id DESC",
           countQuery = "SELECT COUNT(*) FROM leave_schema.leave_application",
           nativeQuery = true)
    Page<LeaveRecord> findAllPaged(Pageable pageable);

    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            WHERE l.user_id = :userId
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = "SELECT COUNT(*) FROM leave_schema.leave_application WHERE user_id = :userId",
           nativeQuery = true)
    Page<LeaveRecord> findByUserIdPaged(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE LOWER(u.user_name) = LOWER(:username)
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM leave_schema.leave_application l
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE LOWER(u.user_name) = LOWER(:username)
            """,
           nativeQuery = true)
    Page<LeaveRecord> findByUsernamePaged(@Param("username") String username, Pageable pageable);

    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE LOWER(u.created_by) = LOWER(:managerUsername)
              AND LOWER(u.role) = 'employee'
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM leave_schema.leave_application l
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE LOWER(u.created_by) = LOWER(:managerUsername)
              AND LOWER(u.role) = 'employee'
            """,
           nativeQuery = true)
    Page<LeaveRecord> findByManagerUsernamePaged(@Param("managerUsername") String managerUsername, Pageable pageable);

    @Query(value = """
            SELECT CAST(ld.leave_date AS varchar) FROM leave_schema.leave_dates ld
            INNER JOIN leave_schema.leave_application l ON ld.leave_application_id = l.id
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE LOWER(u.user_name) = LOWER(:username)
              AND UPPER(COALESCE(l.status, 'PENDING')) IN ('APPROVED', 'PENDING')
              AND ld.leave_date >= CURRENT_DATE
            ORDER BY ld.leave_date ASC
            """, nativeQuery = true)
    List<String> findBookedDatesByUsername(@Param("username") String username);

    @Query(value = """
            SELECT COUNT(*)
            FROM leave_schema.leave_application l
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE LOWER(u.user_name) = LOWER(:username)
              AND UPPER(COALESCE(l.status, 'PENDING')) = UPPER(:status)
            """, nativeQuery = true)
    long countByStatusAndUsername(@Param("username") String username, @Param("status") String status);

    @Query(value = """
            SELECT u.user_name, u.full_name, UPPER(COALESCE(l.status, 'PENDING'))
            FROM leave_schema.leave_dates ld
            INNER JOIN leave_schema.leave_application l ON ld.leave_application_id = l.id
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE ld.leave_date = :date
              AND UPPER(COALESCE(l.status, 'PENDING')) IN ('APPROVED', 'PENDING')
            ORDER BY COALESCE(NULLIF(u.full_name, ''), u.user_name) ASC, u.user_name ASC
            """, nativeQuery = true)
    List<Object[]> findPeopleOnLeaveByDate(@Param("date") LocalDate date);

    @Query(value = """
            SELECT u.user_name, u.full_name, UPPER(COALESCE(l.status, 'PENDING'))
            FROM leave_schema.leave_dates ld
            INNER JOIN leave_schema.leave_application l ON ld.leave_application_id = l.id
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE ld.leave_date = :date
              AND UPPER(COALESCE(l.status, 'PENDING')) IN ('APPROVED', 'PENDING')
              AND (LOWER(u.created_by) = LOWER(:managerUsername) OR LOWER(u.user_name) = LOWER(:managerUsername))
            ORDER BY COALESCE(NULLIF(u.full_name, ''), u.user_name) ASC, u.user_name ASC
            """, nativeQuery = true)
    List<Object[]> findTeamOnLeaveByDateAndManager(@Param("date") LocalDate date, @Param("managerUsername") String managerUsername);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE leave_schema.leave_application SET leave_type_id = NULL WHERE leave_type_id = :leaveTypeId",
           nativeQuery = true)
    int clearLeaveTypeReferenceByLeaveTypeId(@Param("leaveTypeId") Integer leaveTypeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE leave_schema.leave_application
            SET reminder_sent_flags = CASE
              WHEN reminder_sent_flags IS NULL OR reminder_sent_flags = '' THEN :flag
              ELSE reminder_sent_flags || ',' || :flag
            END
            WHERE id = :id
            """, nativeQuery = true)
    void appendReminderFlag(@Param("id") Long id, @Param("flag") String flag);

    @Query(value = "SELECT COUNT(*) > 0 FROM leave_schema.leave_application WHERE user_id = :userId", nativeQuery = true)
    boolean existsByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(SUM(
                CASE WHEN ld.day_type LIKE '%HALF%' THEN 0.5 ELSE 1.0 END
            ), 0)
            FROM leave_schema.leave_dates ld
            INNER JOIN leave_schema.leave_application l ON ld.leave_application_id = l.id
            WHERE l.user_id = :userId
              AND l.leave_type_id = :leaveTypeId
              AND UPPER(COALESCE(l.status, 'PENDING')) = 'APPROVED'
            """, nativeQuery = true)
    Double sumApprovedLeaveDays(@Param("userId") Long userId, @Param("leaveTypeId") Integer leaveTypeId);

    /** Cross-service #19 — find all PENDING leaves for a user being deleted. */
    @Query(value = """
            SELECT l.* FROM leave_schema.leave_application l
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE UPPER(u.user_name) = UPPER(:username)
              AND UPPER(COALESCE(l.status, 'PENDING')) = 'PENDING'
            """, nativeQuery = true)
    List<LeaveRecord> findPendingByUsername(@Param("username") String username);

    /** Cross-service #20 — check if a user has an approved leave on a specific date. */
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM leave_schema.leave_dates ld
            INNER JOIN leave_schema.leave_application l ON ld.leave_application_id = l.id
            INNER JOIN user_schema.user_profile u ON l.user_id = u.id
            WHERE UPPER(u.user_name) = UPPER(:username)
              AND ld.leave_date = :date
              AND UPPER(COALESCE(l.status, 'PENDING')) = 'APPROVED'
            """, nativeQuery = true)
    boolean existsApprovedLeaveOnDate(@Param("username") String username, @Param("date") java.time.LocalDate date);
}
