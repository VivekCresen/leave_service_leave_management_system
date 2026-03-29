package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface LeaveRepository extends JpaRepository<LeaveRecord, Long> {

    List<LeaveRecord> findAllByOrderByFromDateDescIdDesc();

    List<LeaveRecord> findAllByUserIdOrderByFromDateDescIdDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Optional<LeaveRecord> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Stream<LeaveRecord> streamAllByOrderByFromDateDescIdDesc();

    @EntityGraph(attributePaths = {"user", "leaveTypeReference"})
    Stream<LeaveRecord> streamAllByUserIdOrderByFromDateDescIdDesc(Long userId);
}
