package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {

    List<LeaveType> findAllByOrderByIdAsc();

    boolean existsByLeaveNameIgnoreCase(String leaveName);

    boolean existsByLeaveUniqueNameIgnoreCase(String leaveUniqueName);

    boolean existsByLeaveNameIgnoreCaseAndIdNot(String leaveName, Integer id);

    boolean existsByLeaveUniqueNameIgnoreCaseAndIdNot(String leaveUniqueName, Integer id);

    @Query("""
            select leaveType
            from LeaveType leaveType
            where lower(leaveType.leaveName) = lower(:leaveName)
               or lower(leaveType.leaveUniqueName) = lower(:leaveUniqueName)
            """)
    List<LeaveType> findConflicts(@Param("leaveName") String leaveName, @Param("leaveUniqueName") String leaveUniqueName);

    @Query("""
            select leaveType
            from LeaveType leaveType
            where leaveType.id <> :leaveTypeId
              and (
                    lower(leaveType.leaveName) = lower(:leaveName)
                 or lower(leaveType.leaveUniqueName) = lower(:leaveUniqueName)
              )
            """)
    List<LeaveType> findConflictsForUpdate(
            @Param("leaveTypeId") Integer leaveTypeId,
            @Param("leaveName") String leaveName,
            @Param("leaveUniqueName") String leaveUniqueName
    );
}
