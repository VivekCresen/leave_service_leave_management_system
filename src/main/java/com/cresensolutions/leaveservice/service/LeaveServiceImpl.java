package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRepository leaveRepository;
    private final UserProfileRepository userProfileRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveServiceImpl(
            LeaveRepository leaveRepository,
            UserProfileRepository userProfileRepository,
            LeaveTypeRepository leaveTypeRepository
    ) {
        this.leaveRepository = leaveRepository;
        this.userProfileRepository = userProfileRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Override
    @Transactional
    public LeaveResponse createLeave(CreateLeaveRequest request) {
        validateDateRange(request);

        UserProfile user = userProfileRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.userId()));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Inactive users cannot submit leave requests.");
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + request.leaveTypeId()));

        LeaveRecord leave = new LeaveRecord(
                user,
                leaveType,
                request.fromDate(),
                request.toDate(),
                request.reason().trim(),
                request.comments(),
                request.trail(),
                request.editable() == null || request.editable()
        );

        return toLeaveResponse(leaveRepository.save(leave));
    }

    @Override
    public LeaveResponse getLeaveById(Long leaveId) {
        return leaveRepository.findDetailedById(leaveId)
                .map(this::toLeaveResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));
    }

    @Override
    public List<LeaveResponse> getAllLeaves() {
        try (Stream<LeaveRecord> leaves = leaveRepository.streamAllByOrderByFromDateDescIdDesc()) {
            return leaves.map(this::toLeaveResponse)
                    .toList();
        }
    }

    @Override
    public List<LeaveResponse> getLeavesByUserId(Long userId) {
        ensureUserExists(userId);
        try (Stream<LeaveRecord> leaves = leaveRepository.streamAllByUserIdOrderByFromDateDescIdDesc(userId)) {
            return leaves.map(this::toLeaveResponse)
                    .toList();
        }
    }

    @Override
    public List<LeaveTypeResponse> getLeaveTypes() {
        return leaveTypeRepository.findAllByOrderByIdAsc().stream()
                .map(type -> new LeaveTypeResponse(
                        type.getId(),
                        type.getLeaveName(),
                        type.getLeaveUniqueName(),
                        type.getDescription(),
                        type.getMaxDays()
                ))
                .toList();
    }

    private void ensureUserExists(Long userId) {
        if (!userProfileRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
    }

    private void validateDateRange(CreateLeaveRequest request) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw new IllegalArgumentException("To date must be on or after from date.");
        }
    }

    private LeaveResponse toLeaveResponse(LeaveRecord leave) {
        UserProfile user = leave.getUser();
        return new LeaveResponse(
                leave.getId(),
                leave.getUserId(),
                user == null ? null : user.getFullName(),
                leave.getEmailId(),
                leave.getLeaveTypeId(),
                leave.getLeaveType(),
                leave.getFromDate(),
                leave.getToDate(),
                leave.getReason(),
                leave.getComments(),
                leave.getTrail(),
                leave.isEditable(),
                leave.getCreatedAt(),
                leave.getUpdatedAt()
        );
    }
}
