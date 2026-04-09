package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveDateDto;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.NotifyUserResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveRequest;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.model.LeaveNotifyUser;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDateRepository;
import com.cresensolutions.leaveservice.repository.LeaveNotifyUserRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Transactional(readOnly = true)
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRepository leaveRepository;
    private final LeaveDateRepository leaveDateRepository;
    private final UserProfileRepository userProfileRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveNotifyUserRepository leaveNotifyUserRepository;
    private final LeaveEmailService leaveEmailService;
    private final Executor leaveTaskExecutor;

    public LeaveServiceImpl(
            LeaveRepository leaveRepository,
            LeaveDateRepository leaveDateRepository,
            UserProfileRepository userProfileRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeLeaveRepository employeeLeaveRepository,
            LeaveNotifyUserRepository leaveNotifyUserRepository,
            LeaveEmailService leaveEmailService,
            @Qualifier("leaveTaskExecutor") Executor leaveTaskExecutor
    ) {
        this.leaveRepository = leaveRepository;
        this.leaveDateRepository = leaveDateRepository;
        this.userProfileRepository = userProfileRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveNotifyUserRepository = leaveNotifyUserRepository;
        this.leaveEmailService = leaveEmailService;
        this.leaveTaskExecutor = leaveTaskExecutor;
    }

    @Override
    @Transactional
    public LeaveResponse createLeave(CreateLeaveRequest request) {
        if (request.leaveDates() == null || request.leaveDates().isEmpty()) {
            throw new IllegalArgumentException("At least one leave date is required.");
        }

        UserProfile user = resolveUser(request);
        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave type not found with id: " + request.leaveTypeId()));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Inactive users cannot submit leave requests.");
        }

        validateGenderRestriction(leaveType, user);

        String leaveUniqueName = leaveType.getLeaveUniqueName();
        if (leaveUniqueName != null && !leaveUniqueName.isBlank()) {
            Double remaining = employeeLeaveRepository.getRemainingBalance(user.getId(), leaveUniqueName);
            if (remaining != null && remaining <= 0) {
                throw new IllegalArgumentException(
                        "You have no remaining " + leaveType.getLeaveName() + " balance.");
            }
        }

        LeaveRecord leave = new LeaveRecord(
                user, leaveType,
                request.reason().trim(),
                request.comments(),
                request.trail(),
                request.editable() == null || request.editable()
        );

        LeaveRecord saved = leaveRepository.save(leave);

        List<LeaveDate> dates = request.leaveDates().stream()
                .map(dto -> new LeaveDate(saved, dto.date(), dto.dayType()))
                .toList();
        leaveDateRepository.saveAll(dates);

        saveNotifyUsers(saved, user.getId(), request.notifyUserIds());

        return toLeaveResponse(saved);
    }

    @Override
    public LeaveResponse getLeaveById(Long leaveId) {
        return leaveRepository.findDetailedById(leaveId)
                .map(this::toLeaveResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));
    }

    @Override
    public Page<LeaveResponse> getAllLeaves(int page, int size) {
        return leaveRepository.findAllPaged(buildPageable(page, size)).map(this::toLeaveResponse);
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

        if ("REJECTED".equals(status) && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required when rejecting a leave.");
        }

        leave.updateStatus(status, request.actorUsername(), request.rejectionReason());
        LeaveRecord saved = leaveRepository.save(leave);

        if ("APPROVED".equals(status)) {
            Long userId = leave.getUserId();
            Integer leaveTypeId = leave.getLeaveTypeId();
            List<LeaveDate> dates = leaveDateRepository.findByApplicationId(saved.getId());
            double days = dates.stream()
                    .mapToDouble(d -> d.getDayType() != null && d.getDayType().contains("HALF") ? 0.5 : 1.0)
                    .sum();

            CompletableFuture.runAsync(
                    () -> deductLeaveBalance(userId, leaveTypeId, days),
                    leaveTaskExecutor
            ).exceptionally(ex -> {
                System.err.printf("[LeaveService] Failed to deduct balance for userId=%d: %s%n",
                        userId, ex.getMessage());
                return null;
            });

            List<String> notifyEmails = leaveNotifyUserRepository.findByLeaveId(saved.getId())
                    .stream().map(LeaveNotifyUser::getUserEmail)
                    .filter(e -> e != null && !e.isBlank()).toList();

            if (!notifyEmails.isEmpty()) {
                String employeeName = saved.getUser() != null ? saved.getUser().getFullName() : "A team member";
                LocalDate from = dates.stream().map(LeaveDate::getLeaveDate).min(LocalDate::compareTo).orElse(null);
                LocalDate to = dates.stream().map(LeaveDate::getLeaveDate).max(LocalDate::compareTo).orElse(null);
                leaveEmailService.sendLeaveApprovedNotification(
                        notifyEmails, employeeName, saved.getLeaveType(),
                        from, to, false, null, saved.getReason()
                );
            }
        }

        return toLeaveResponse(saved);
    }

    @Override
    @Transactional
    public LeaveResponse updateLeave(Long leaveId, UpdateLeaveRequest request) {
        LeaveRecord leave = leaveRepository.findDetailedById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));

        if (!"PENDING".equalsIgnoreCase(leave.getStatus())) {
            throw new IllegalArgumentException("Only PENDING leave applications can be edited.");
        }
        if (request.leaveDates() == null || request.leaveDates().isEmpty()) {
            throw new IllegalArgumentException("At least one leave date is required.");
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave type not found with id: " + request.leaveTypeId()));

        leave.updateDetails(leaveType, request.reason().trim(), request.comments(), request.trail());
        LeaveRecord saved = leaveRepository.save(leave);

        leaveDateRepository.deleteByApplicationId(saved.getId());
        List<LeaveDate> newDates = request.leaveDates().stream()
                .map(dto -> new LeaveDate(saved, dto.date(), dto.dayType()))
                .toList();
        leaveDateRepository.saveAll(newDates);

        leaveNotifyUserRepository.deleteByLeaveId(saved.getId());
        saveNotifyUsers(saved, saved.getUserId(), request.notifyUserIds());

        return toLeaveResponse(saved);
    }

    @Override
    @Transactional
    public void deletePendingLeave(Long leaveId) {
        LeaveRecord leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));
        if (!"PENDING".equalsIgnoreCase(leave.getStatus())) {
            throw new IllegalArgumentException("Only PENDING leave applications can be deleted.");
        }
        leaveNotifyUserRepository.deleteByLeaveId(leaveId);
        leaveDateRepository.deleteByApplicationId(leaveId);
        leaveRepository.deleteById(leaveId);
    }

    @Override
    public List<LeaveTypeResponse> getLeaveTypes() {
        return leaveTypeRepository.findAllOrderedById().stream().map(this::toLeaveTypeResponse).toList();
    }

    @Override
    @Transactional
    public LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request) {
        String leaveName = requireNonBlank(request.leaveName(), "Leave name is required");
        String leaveUniqueName = normalizeUniqueName(request.leaveUniqueName());
        Integer maxDays = Optional.ofNullable(request.maxDays())
                .orElseThrow(() -> new IllegalArgumentException("Max days is required."));
        validateNoConflicts(leaveTypeRepository.findConflicts(leaveName, leaveUniqueName), leaveName, leaveUniqueName);
        LeaveType leaveType = new LeaveType(leaveName, leaveUniqueName, trimOrNull(request.description()),
                maxDays, normalizeGender(request.genderRestriction()));
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
        validateNoConflicts(leaveTypeRepository.findConflictsForUpdate(leaveTypeId, leaveName, leaveUniqueName),
                leaveName, leaveUniqueName);
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

    @Override
    public List<NotifyUserResponse> getNotifyUsers(String username) {
        String normalized = requireNonBlank(username, "Username is required.");
        UserProfile requestingUser = userProfileRepository.findByUserName(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + normalized));
        String role = requestingUser.getRole() == null ? "" : requestingUser.getRole().toUpperCase();
        List<UserProfile> candidates;
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) {
            candidates = userProfileRepository.findAllActiveExcept(requestingUser.getId());
        } else {
            String createdBy = requestingUser.getCreatedBy();
            if (createdBy == null || createdBy.isBlank()) return Collections.emptyList();
            candidates = userProfileRepository.findActiveByManagerUsername(createdBy).stream()
                    .filter(u -> !u.getId().equals(requestingUser.getId())).toList();
        }
        return candidates.stream()
                .map(u -> new NotifyUserResponse(u.getId(), u.getFullName(), u.getEmailId(), u.getRole()))
                .toList();
    }

    private void saveNotifyUsers(LeaveRecord saved, Long ownerId, List<Long> notifyUserIds) {
        if (notifyUserIds == null || notifyUserIds.isEmpty()) return;
        List<LeaveNotifyUser> entries = notifyUserIds.stream()
                .distinct()
                .filter(uid -> !uid.equals(ownerId))
                .map(uid -> userProfileRepository.findById(uid).orElse(null))
                .filter(u -> u != null)
                .map(u -> new LeaveNotifyUser(saved, u))
                .toList();
        leaveNotifyUserRepository.saveAll(entries);
    }

    @Transactional
    protected void deductLeaveBalance(Long userId, Integer leaveTypeId, double days) {
        if (userId == null || leaveTypeId == null) return;
        String leaveUniqueName = leaveTypeRepository.findUniqueNameById(leaveTypeId);
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) return;
        employeeLeaveRepository.deductLeaveBalance(userId, leaveUniqueName, days);
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

    private void validateGenderRestriction(LeaveType leaveType, UserProfile user) {
        String restriction = leaveType.getGenderRestriction();
        if (restriction == null || restriction.isBlank()) return;
        String userGender = user.getGender() == null ? "" : user.getGender().toUpperCase();
        if (!restriction.equalsIgnoreCase(userGender)) {
            String allowed = restriction.charAt(0) + restriction.substring(1).toLowerCase();
            throw new IllegalArgumentException(leaveType.getLeaveName() + " is only available for " + allowed + " employees.");
        }
    }

    private void validateNoConflicts(List<LeaveType> conflicts, String leaveName, String leaveUniqueName) {
        conflicts.stream().filter(c -> c.getLeaveName() != null && c.getLeaveName().equalsIgnoreCase(leaveName))
                .findFirst().ifPresent(c -> { throw new IllegalArgumentException("Leave name already exists: " + leaveName); });
        conflicts.stream().filter(c -> c.getLeaveUniqueName() != null && c.getLeaveUniqueName().equalsIgnoreCase(leaveUniqueName))
                .findFirst().ifPresent(c -> { throw new IllegalArgumentException("Leave unique name already exists: " + leaveUniqueName); });
    }

    private LeaveResponse toLeaveResponse(LeaveRecord leave) {
        UserProfile user = leave.getUser();
        List<LeaveDateDto> dates = leaveDateRepository.findByApplicationId(leave.getId())
                .stream().map(d -> new LeaveDateDto(d.getLeaveDate(), d.getDayType())).toList();
        List<Long> notifyUserIds = leaveNotifyUserRepository.findByLeaveId(leave.getId())
                .stream().map(LeaveNotifyUser::getUserId).toList();
        return new LeaveResponse(
                leave.getId(), leave.getUserId(),
                user == null ? null : user.getFullName(),
                leave.getEmailId(), leave.getLeaveTypeId(), leave.getLeaveType(),
                dates, leave.getReason(), leave.getComments(), leave.getTrail(),
                leave.isEditable(), leave.getStatus() != null ? leave.getStatus() : "PENDING",
                leave.getApprovedBy(), leave.getRejectionReason(),
                leave.getCreatedAt(), leave.getUpdatedAt(), notifyUserIds
        );
    }

    private LeaveTypeResponse toLeaveTypeResponse(LeaveType type) {
        return new LeaveTypeResponse(type.getId(), type.getLeaveName(), type.getLeaveUniqueName(),
                type.getDescription(), type.getMaxDays(), type.getGenderRestriction(),
                type.getCreatedAt(), type.getUpdatedAt());
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
}
