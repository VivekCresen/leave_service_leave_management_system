package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.EmployeeLeave;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Service
@Transactional(readOnly = true)
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRepository leaveRepository;
    private final UserProfileRepository userProfileRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final Executor leaveTaskExecutor;

    public LeaveServiceImpl(
            LeaveRepository leaveRepository,
            UserProfileRepository userProfileRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeLeaveRepository employeeLeaveRepository,
            @Qualifier("leaveTaskExecutor") Executor leaveTaskExecutor
    ) {
        this.leaveRepository = leaveRepository;
        this.userProfileRepository = userProfileRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveTaskExecutor = leaveTaskExecutor;
    }

    @Override
    @Transactional
    public LeaveResponse createLeave(CreateLeaveRequest request) {
        validateDateRange(request);

        UserProfile user = resolveUser(request);
        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave type not found with id: " + request.leaveTypeId()));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Inactive users cannot submit leave requests.");
        }

        validateGenderRestriction(leaveType, user);

        // Block if a PENDING leave of the same type already exists
        if (leaveRepository.existsPendingLeaveByUserAndType(user.getId(), leaveType.getId())) {
            throw new IllegalArgumentException(
                    "You already have a pending " + leaveType.getLeaveName() + " request. Please wait for it to be processed.");
        }

        // Block if no balance remaining for this leave type
        String leaveUniqueName = leaveType.getLeaveUniqueName();
        if (leaveUniqueName != null && !leaveUniqueName.isBlank()) {
            Integer remaining = employeeLeaveRepository.getRemainingBalance(user.getId(), leaveUniqueName);
            if (remaining != null && remaining <= 0) {
                throw new IllegalArgumentException(
                        "You have no remaining " + leaveType.getLeaveName() + " balance.");
            }
        }

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
        return leaveRepository.findAllPaged(buildPageable(page, size))
                .map(this::toLeaveResponse);
    }

    @Override
    public Page<LeaveResponse> getLeavesByUserId(Long userId, int page, int size) {
        Page<LeaveResponse> result = leaveRepository
                .findByUserIdPaged(userId, buildPageable(page, size))
                .map(this::toLeaveResponse);

        if (!result.hasContent() && !userProfileRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return result;
    }

    @Override
    public Page<LeaveResponse> getLeavesByUsername(String username, int page, int size) {
        String normalized = requireNonBlank(username, "Username is required.");

       userProfileRepository.findByUserName(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + normalized));

        return leaveRepository.findByUsernamePaged(normalized, buildPageable(page, size))
                .map(this::toLeaveResponse);
    }

    @Override
    public Page<LeaveResponse> getLeavesByManagerUsername(String managerUsername, int page, int size) {
        String normalized = requireNonBlank(managerUsername, "Manager username is required.");
        return leaveRepository.findByManagerUsernamePaged(normalized, buildPageable(page, size))
                .map(this::toLeaveResponse);
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
                throw new IllegalArgumentException("Rejection reason is required when rejecting a leave.");
            }
        }

        leave.updateStatus(status, request.actorUsername(), request.rejectionReason());
        LeaveRecord saved = leaveRepository.save(leave);

       if ("APPROVED".equals(status)) {
            Long userId = leave.getUserId();
            Integer leaveTypeId = leave.getLeaveTypeId();
            long days = ChronoUnit.DAYS.between(leave.getFromDate(), leave.getToDate()) + 1;

            CompletableFuture.runAsync(
                    () -> deductLeaveBalance(userId, leaveTypeId, (int) days),
                    leaveTaskExecutor
            ).exceptionally(ex -> {
                // Log but don't fail the status update
                System.err.printf("[LeaveService] Failed to deduct balance for userId=%d: %s%n",
                        userId, ex.getMessage());
                return null;
            });
        }

        return toLeaveResponse(saved);
    }

    @Override
    public List<LeaveTypeResponse> getLeaveTypes() {
      
        return leaveTypeRepository.findAllOrderedById().stream()
                .map(this::toLeaveTypeResponse)
                .toList(); 
    }

    @Override
    @Transactional
    public LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request) {
        String leaveName = requireNonBlank(request.leaveName(), "Leave name is required");
        String leaveUniqueName = normalizeUniqueName(request.leaveUniqueName());
        Integer maxDays = Optional.ofNullable(request.maxDays())
                .orElseThrow(() -> new IllegalArgumentException("Max days is required."));

        validateNoConflicts(leaveTypeRepository.findConflicts(leaveName, leaveUniqueName), leaveName, leaveUniqueName);

        LeaveType leaveType = new LeaveType(
                leaveName,
                leaveUniqueName,
                trimOrNull(request.description()),
                maxDays,
                normalizeGender(request.genderRestriction())
        );
        return toLeaveTypeResponse(leaveTypeRepository.save(leaveType));
    }

    @Override
    @Transactional
    public LeaveTypeResponse updateLeaveType(Integer leaveTypeId, CreateLeaveTypeRequest request) {
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + leaveTypeId));

        String leaveName = requireNonBlank(request.leaveName(), "Leave name is required");
        String leaveUniqueName = normalizeUniqueName(request.leaveUniqueName());
        Integer maxDays = Optional.ofNullable(request.maxDays())
                .orElseThrow(() -> new IllegalArgumentException("Max days is required."));

        validateNoConflicts(
                leaveTypeRepository.findConflictsForUpdate(leaveTypeId, leaveName, leaveUniqueName),
                leaveName, leaveUniqueName
        );

        leaveType.updateDetails(leaveName, leaveUniqueName, trimOrNull(request.description()),
                maxDays, normalizeGender(request.genderRestriction()));
        return toLeaveTypeResponse(leaveTypeRepository.save(leaveType));
    }

    @Override
    @Transactional
    public void deleteLeaveType(Integer leaveTypeId) {
        if (!leaveTypeRepository.existsById(leaveTypeId)) {
            throw new ResourceNotFoundException("Leave type not found with id: " + leaveTypeId);
        }
        leaveRepository.clearLeaveTypeReferenceByLeaveTypeId(leaveTypeId);
        leaveTypeRepository.deleteById(leaveTypeId);
    }

    @Transactional
    protected void deductLeaveBalance(Long userId, Integer leaveTypeId, int days) {
        if (userId == null || leaveTypeId == null) return;

        String leaveUniqueName = leaveTypeRepository.findUniqueNameById(leaveTypeId);
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) return;

       employeeLeaveRepository.deductLeaveBalance(userId, leaveUniqueName, days);
    }

    private UserProfile resolveUser(CreateLeaveRequest request) {
        if (request.userId() != null) {
            return userProfileRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found with id: " + request.userId()));
        }
        if (request.username() != null && !request.username().isBlank()) {
            return userProfileRepository.findByUserName(request.username().trim())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found with username: " + request.username()));
        }
        throw new IllegalArgumentException("Either userId or username must be provided.");
    }

    private void validateGenderRestriction(LeaveType leaveType, UserProfile user) {
        String restriction = leaveType.getGenderRestriction();
        if (restriction == null || restriction.isBlank()) return;

        String userGender = user.getGender() == null ? "" : user.getGender().toUpperCase();
        if (!restriction.equalsIgnoreCase(userGender)) {
            String allowed = restriction.charAt(0) + restriction.substring(1).toLowerCase();
            throw new IllegalArgumentException(
                    leaveType.getLeaveName() + " is only available for " + allowed + " employees.");
        }
    }

    private void validateDateRange(CreateLeaveRequest request) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw new IllegalArgumentException("To date must be on or after from date.");
        }
    }

    private void validateNoConflicts(List<LeaveType> conflicts, String leaveName, String leaveUniqueName) {
      conflicts.stream()
                .filter(c -> c.getLeaveName() != null && c.getLeaveName().equalsIgnoreCase(leaveName))
                .findFirst()
                .ifPresent(c -> { throw new IllegalArgumentException("Leave name already exists: " + leaveName); });

        conflicts.stream()
                .filter(c -> c.getLeaveUniqueName() != null && c.getLeaveUniqueName().equalsIgnoreCase(leaveUniqueName))
                .findFirst()
                .ifPresent(c -> { throw new IllegalArgumentException("Leave unique name already exists: " + leaveUniqueName); });
    }

    private Pageable buildPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String normalizeUniqueName(String value) {
        return requireNonBlank(value, "Leave unique name is required").replace(' ', '_').toUpperCase();
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeGender(String value) {
        if (value == null || value.isBlank()) return null;
        String upper = value.trim().toUpperCase();
        return (upper.equals("MALE") || upper.equals("FEMALE")) ? upper : null;
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

    private LeaveTypeResponse toLeaveTypeResponse(LeaveType type) {
        return new LeaveTypeResponse(
                type.getId(),
                type.getLeaveName(),
                type.getLeaveUniqueName(),
                type.getDescription(),
                type.getMaxDays(),
                type.getGenderRestriction(),
                type.getCreatedAt(),
                type.getUpdatedAt()
        );
    }
}
