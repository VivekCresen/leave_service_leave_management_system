package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LeaveRepository extends JpaRepository<LeaveRecord, Long> {

    @Query(value = """
            SELECT l.* FROM leave_application l
            LEFT JOIN user_profile u ON l.user_id = u.id
            LEFT JOIN leave_types lt ON l.leave_type_id = lt.id
            WHERE l.id = :id
            """, nativeQuery = true)
    Optional<LeaveRecord> findDetailedById(@Param("id") Long id);

    @Query(value = "SELECT l.* FROM leave_application l ORDER BY l.created_at DESC, l.id DESC",
           countQuery = "SELECT COUNT(*) FROM leave_application",
           nativeQuery = true)
    Page<LeaveRecord> findAllPaged(Pageable pageable);

    @Query(value = """
            SELECT l.* FROM leave_application l
            WHERE l.user_id = :userId
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = "SELECT COUNT(*) FROM leave_application WHERE user_id = :userId",
           nativeQuery = true)
    Page<LeaveRecord> findByUserIdPaged(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT l.* FROM leave_application l
            INNER JOIN user_profile u ON l.user_id = u.id
            WHERE LOWER(u.user_name) = LOWER(:username)
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM leave_application l
            INNER JOIN user_profile u ON l.user_id = u.id
            WHERE LOWER(u.user_name) = LOWER(:username)
            """,
           nativeQuery = true)
    Page<LeaveRecord> findByUsernamePaged(@Param("username") String username, Pageable pageable);

    @Query(value = """
            SELECT l.* FROM leave_application l
            INNER JOIN user_profile u ON l.user_id = u.id
            WHERE LOWER(u.created_by) = LOWER(:managerUsername)
              AND LOWER(u.role) = 'employee'
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM leave_application l
            INNER JOIN user_profile u ON l.user_id = u.id
            WHERE LOWER(u.created_by) = LOWER(:managerUsername)
              AND LOWER(u.role) = 'employee'
            """,
           nativeQuery = true)
    Page<LeaveRecord> findByManagerUsernamePaged(@Param("managerUsername") String managerUsername, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE leave_application SET leave_type_id = NULL WHERE leave_type_id = :leaveTypeId",
           nativeQuery = true)
    int clearLeaveTypeReferenceByLeaveTypeId(@Param("leaveTypeId") Integer leaveTypeId);

    @Query(value = "SELECT COUNT(*) > 0 FROM leave_application WHERE user_id = :userId", nativeQuery = true)
    boolean existsByUserId(@Param("userId") Long userId);
}
