package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

        UserProfile user = resolveUser(request);

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
    public Page<LeaveResponse> getAllLeaves(int page, int size) {
        return leaveRepository.findAllByOrderByFromDateDescIdDesc(buildPageable(page, size))
                .map(this::toLeaveResponse);
    }

    @Override
    public Page<LeaveResponse> getLeavesByUserId(Long userId, int page, int size) {
        Page<LeaveResponse> leaves = leaveRepository
                .findAllByUserIdOrderByFromDateDescIdDesc(userId, buildPageable(page, size))
                .map(this::toLeaveResponse);

        if (leaves.hasContent()) {
            return leaves;
        }

        ensureUserExists(userId);
        return leaves;
    }

    @Override
    @Transactional
    public LeaveResponse updateLeaveStatus(Long leaveId, UpdateLeaveStatusRequest request) {
        LeaveRecord leave = leaveRepository.findDetailedById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));

        String status = request.status().toUpperCase();

        if ("REJECTED".equals(status)) {
            String reason = request.rejectionReason();
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Rejection reason is required when rejecting a leave request.");
            }
        }

        leave.updateStatus(status, request.actorUsername(), request.rejectionReason());
        return toLeaveResponse(leaveRepository.save(leave));
    }

    @Override
    public List<LeaveTypeResponse> getLeaveTypes() {
        return leaveTypeRepository.findAllByOrderByIdAsc().stream()
                .map(this::toLeaveTypeResponse)
                .toList();
    }

    @Override
    @Transactional
    public LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request) {
        String leaveName = normalizeRequiredValue(request.leaveName(), "Leave name is required");
        String leaveUniqueName = normalizeUniqueName(request.leaveUniqueName());
        String description = normalizeOptionalValue(request.description());
        Integer maxDays = request.maxDays();

        if (maxDays == null) {
            throw new IllegalArgumentException("Max days is required.");
        }

        validateNoLeaveTypeConflicts(leaveTypeRepository.findConflicts(leaveName, leaveUniqueName), leaveName, leaveUniqueName);

        LeaveType leaveType = new LeaveType(leaveName, leaveUniqueName, description, maxDays);
        return toLeaveTypeResponse(leaveTypeRepository.save(leaveType));
    }

    @Override
    @Transactional
    public LeaveTypeResponse updateLeaveType(Integer leaveTypeId, CreateLeaveTypeRequest request) {
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + leaveTypeId));

        String leaveName = normalizeRequiredValue(request.leaveName(), "Leave name is required");
        String leaveUniqueName = normalizeUniqueName(request.leaveUniqueName());
        String description = normalizeOptionalValue(request.description());
        Integer maxDays = request.maxDays();

        if (maxDays == null) {
            throw new IllegalArgumentException("Max days is required.");
        }

        validateNoLeaveTypeConflicts(
                leaveTypeRepository.findConflictsForUpdate(leaveTypeId, leaveName, leaveUniqueName),
                leaveName,
                leaveUniqueName
        );

        leaveType.updateDetails(leaveName, leaveUniqueName, description, maxDays);
        return toLeaveTypeResponse(leaveTypeRepository.save(leaveType));
    }

    @Override
    @Transactional
    public void deleteLeaveType(Integer leaveTypeId) {
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + leaveTypeId));

        leaveRepository.clearLeaveTypeReferenceByLeaveTypeId(leaveTypeId);
        leaveTypeRepository.delete(leaveType);
    }

    private void ensureUserExists(Long userId) {
        if (!userProfileRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
    }

    private UserProfile resolveUser(CreateLeaveRequest request) {
        if (request.userId() != null) {
            return userProfileRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.userId()));
        }

        if (request.username() != null && !request.username().isBlank()) {
            return userProfileRepository.findByUserName(request.username().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + request.username()));
        }

        throw new IllegalArgumentException("Either userId or username must be provided.");
    }

    private Pageable buildPageable(int page, int size) {
        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(sanitizedPage, sanitizedSize);
    }

    private void validateDateRange(CreateLeaveRequest request) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw new IllegalArgumentException("To date must be on or after from date.");
        }
    }

    private String normalizeRequiredValue(String value, String message) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeUniqueName(String value) {
        String normalized = normalizeRequiredValue(value, "Leave unique name is required");
        return normalized.replace(' ', '_').toUpperCase();
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateNoLeaveTypeConflicts(List<LeaveType> conflicts, String leaveName, String leaveUniqueName) {
        for (LeaveType conflict : conflicts) {
            if (conflict.getLeaveName() != null && conflict.getLeaveName().equalsIgnoreCase(leaveName)) {
                throw new IllegalArgumentException("Leave name already exists: " + leaveName);
            }

            if (conflict.getLeaveUniqueName() != null && conflict.getLeaveUniqueName().equalsIgnoreCase(leaveUniqueName)) {
                throw new IllegalArgumentException("Leave unique name already exists: " + leaveUniqueName);
            }
        }
    }

    private LeaveTypeResponse toLeaveTypeResponse(LeaveType type) {
        return new LeaveTypeResponse(
                type.getId(),
                type.getLeaveName(),
                type.getLeaveUniqueName(),
                type.getDescription(),
                type.getMaxDays(),
                type.getCreatedAt(),
                type.getUpdatedAt()
        );
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
                leave.getStatus() != null ? leave.getStatus() : "PENDING",
                leave.getApprovedBy(),
                leave.getRejectionReason(),
                leave.getCreatedAt(),
                leave.getUpdatedAt()
        );
    }
}
