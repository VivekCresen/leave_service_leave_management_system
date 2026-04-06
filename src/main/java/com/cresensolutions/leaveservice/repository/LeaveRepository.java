package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface LeaveRepository extends JpaRepository<LeaveRecord, Long> {

    List<LeaveRecord> findAllByOrderByFromDateDescIdDesc();

    List<LeaveRecord> findAllByUserIdOrderByFromDateDescIdDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Page<LeaveRecord> findAllByOrderByFromDateDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Page<LeaveRecord> findAllByUserIdOrderByFromDateDescIdDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Optional<LeaveRecord> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Stream<LeaveRecord> streamAllByOrderByFromDateDescIdDesc();

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Stream<LeaveRecord> streamAllByUserIdOrderByFromDateDescIdDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LeaveRecord leave set leave.leaveTypeReference = null where leave.leaveTypeReference.id = :leaveTypeId")
    int clearLeaveTypeReferenceByLeaveTypeId(@Param("leaveTypeId") Integer leaveTypeId);
}
