package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.dto.PartialLeaveStatusRequest;
import com.cresensolutions.leaveservice.dto.AppendAuditTrailRequest;
import com.cresensolutions.leaveservice.dto.AuditTrailEntryDto;
import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveDateDto;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.MailLeaveDecisionRequest;
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
import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.common.StringUtils;
import com.cresensolutions.leaveservice.service.LeaveBalanceService;
import com.cresensolutions.leaveservice.service.LeaveEmailService;
import com.cresensolutions.leaveservice.service.LeaveReminderDispatchService;
import com.cresensolutions.leaveservice.service.LeaveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
@Slf4j
@Service("leaveService")
@Transactional(readOnly = true)
public class LeaveServiceImpl implements LeaveService {
    private static final String PROCESS_DEF_KEY = LeaveConstants.PROCESS_DEF_KEY;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final LeaveRepository leaveRepository;
    private final LeaveDateRepository leaveDateRepository;
    private final UserProfileRepository userProfileRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveNotifyUserRepository leaveNotifyUserRepository;
    private final LeaveEmailService leaveEmailService;
    private final LeaveReminderDispatchService leaveReminderDispatchService;
    private final LeaveBalanceService leaveBalanceService;
    private final Executor leaveTaskExecutor;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final String loginUrl;

    public LeaveServiceImpl(
            LeaveRepository leaveRepository,
            LeaveDateRepository leaveDateRepository,
            UserProfileRepository userProfileRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeLeaveRepository employeeLeaveRepository,
            LeaveNotifyUserRepository leaveNotifyUserRepository,
            LeaveEmailService leaveEmailService,
            LeaveReminderDispatchService leaveReminderDispatchService,
            LeaveBalanceService leaveBalanceService,
            @Qualifier("leaveTaskExecutor") Executor leaveTaskExecutor,
            RuntimeService runtimeService,
            TaskService taskService,
            @Value("${app.login-url:http://localhost:3000/login}") String loginUrl
    ) {
        this.leaveRepository = leaveRepository;
        this.leaveDateRepository = leaveDateRepository;
        this.userProfileRepository = userProfileRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveNotifyUserRepository = leaveNotifyUserRepository;
        this.leaveEmailService = leaveEmailService;
        this.leaveReminderDispatchService = leaveReminderDispatchService;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveTaskExecutor = leaveTaskExecutor;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.loginUrl = loginUrl;
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

        validateRequestedLeaveBalance(user, leaveType, request.leaveDates());

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

        saved.appendTrailEntry(LeaveConstants.EVENT_SUBMITTED, user.getUserName(), null, null,
                "Leave request submitted by " + user.getUserName());
        leaveRepository.save(saved);

        startApprovalProcess(saved, user, request);

        return toLeaveResponse(saved);
    }

    @Override
    public LeaveResponse getLeaveById(Long leaveId) {
        return toLeaveResponse(findLeaveOrThrow(leaveId));
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
        LeaveRecord leave = findLeaveOrThrow(leaveId);

        String status = request.status().toUpperCase();

        if (LeaveConstants.STATUS_REJECTED.equals(status) && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required when rejecting a leave.");
        }

        if (LeaveConstants.STATUS_MANAGER_APPROVED.equals(status)) {
            if (!LeaveConstants.STATUS_PENDING.equals(leave.getStatus())) {
                throw new IllegalArgumentException(
                        "Leave must be PENDING for manager approval. Current status: " + leave.getStatus());
            }

            Task managerTask = findTaskByDefinitionKey(leaveId, "task_manager_approval");
            if (managerTask != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("actorUsername", request.actorUsername());
                vars.put("status", "APPROVED"); // BPMN gateway reads "APPROVED" → routes to MANAGER_APPROVED service task
                taskService.complete(managerTask.getId(), vars);
                log.info("[LeaveService] Manager task {} completed for leaveId={}", managerTask.getId(), leaveId);
                return toLeaveResponse(findLeaveOrThrow(leaveId));
            }

            leave.setManagerApproved(request.actorUsername());
            leave.appendTrailEntry(LeaveConstants.STATUS_MANAGER_APPROVED, request.actorUsername(),
                    null, null,
                    "Approved by manager " + request.actorUsername() + ". Pending admin final approval.");
            leaveRepository.save(leave);

            List<LeaveDate> dates = leaveDateRepository.findByApplicationId(leave.getId());
            String employeeName = leave.getUser() != null && leave.getUser().getFullName() != null
                    ? leave.getUser().getFullName() : LeaveConstants.DEFAULT_EMPLOYEE_NAME;
            List<String> adminEmails = userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN).stream()
                    .map(UserProfile::getEmailId).filter(e -> e != null && !e.isBlank()).toList();
            leaveEmailService.sendManagerApprovedPendingAdminNotification(
                    adminEmails, resolveStatusRecipients(leave), employeeName,
                    leave.getLeaveType(), dates, leave.getReason(),
                    resolveActorDisplayName(request.actorUsername()), leaveId);

            return toLeaveResponse(findLeaveOrThrow(leaveId));
        }

        if (LeaveConstants.STATUS_APPROVED.equals(status)) {
            if (!LeaveConstants.STATUS_MANAGER_APPROVED.equals(leave.getStatus())) {
                throw new IllegalArgumentException(
                        "Final approval requires leave to be in MANAGER_APPROVED state. Current status: " + leave.getStatus());
            }

            Task adminTask = findTaskByDefinitionKey(leaveId, "task_admin_approval");
            if (adminTask != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("actorUsername", request.actorUsername());
                vars.put("status", "APPROVED");
                taskService.complete(adminTask.getId(), vars);
                log.info("[LeaveService] Admin task {} completed (APPROVED) for leaveId={}", adminTask.getId(), leaveId);
                return toLeaveResponse(findLeaveOrThrow(leaveId));
            }

            leave.setAdminApproved(request.actorUsername());
            leave.appendTrailEntry(status, request.actorUsername(), null, null,
                    "Final approval by admin " + request.actorUsername());
            LeaveRecord saved = leaveRepository.save(leave);
            List<LeaveDate> dates = leaveDateRepository.findByApplicationId(saved.getId());
            String employeeName = saved.getUser() != null && saved.getUser().getFullName() != null
                    ? saved.getUser().getFullName() : LeaveConstants.DEFAULT_EMPLOYEE_NAME;

            double days = dates.stream()
                    .mapToDouble(d -> d.getDayType() != null
                            && d.getDayType().contains(LeaveConstants.DAY_TYPE_HALF_KEYWORD) ? 0.5 : 1.0)
                    .sum();
            final Long userId = leave.getUserId();
            final Integer leaveTypeId = leave.getLeaveTypeId();
            CompletableFuture.runAsync(
                    () -> leaveBalanceService.deductLeaveBalance(userId, leaveTypeId, days),
                    leaveTaskExecutor
            ).exceptionally(ex -> {
                log.error("[LeaveService] Failed to deduct balance for userId={}: {}", userId, ex.getMessage());
                return null;
            });

            leaveEmailService.sendLeaveStatusNotification(
                    resolveStatusRecipients(saved), employeeName, saved.getLeaveType(), dates,
                    saved.getReason(), status,
                    resolveActorDisplayName(request.actorUsername()),
                    resolveActorRoleDisplay(request.actorUsername()), null);

            return toLeaveResponse(saved);
        }

        if (LeaveConstants.STATUS_REJECTED.equals(status)) {
            Task activeTask = findTaskByDefinitionKey(leaveId, "task_manager_approval");
            if (activeTask == null) {
                activeTask = findTaskByDefinitionKey(leaveId, "task_admin_approval");
            }
            if (activeTask != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("actorUsername", request.actorUsername());
                vars.put("status", "REJECTED");
                vars.put("rejectionReason", request.rejectionReason());
                taskService.complete(activeTask.getId(), vars);
                log.info("[LeaveService] Task {} completed (REJECTED) for leaveId={}", activeTask.getId(), leaveId);
                return toLeaveResponse(findLeaveOrThrow(leaveId));
            }

            if (LeaveConstants.STATUS_MANAGER_APPROVED.equalsIgnoreCase(leave.getStatus())) {
                leave.setAdminRejected(request.actorUsername(), request.rejectionReason());
            } else {
                leave.setManagerRejected(request.actorUsername(), request.rejectionReason());
            }
            leave.appendTrailEntry(status, request.actorUsername(), null, null,
                    "Rejected by " + resolveActorDisplayName(request.actorUsername()) + (request.rejectionReason() != null && !request.rejectionReason().isBlank() ? ": " + request.rejectionReason() : ""));
            LeaveRecord saved = leaveRepository.save(leave);
            List<LeaveDate> dates = leaveDateRepository.findByApplicationId(saved.getId());
            String employeeName = saved.getUser() != null && saved.getUser().getFullName() != null
                    ? saved.getUser().getFullName() : LeaveConstants.DEFAULT_EMPLOYEE_NAME;

            leaveEmailService.sendLeaveStatusNotification(
                    resolveStatusRecipients(saved), employeeName, saved.getLeaveType(), dates,
                    saved.getReason(), status,
                    resolveActorDisplayName(request.actorUsername()),
                    resolveActorRoleDisplay(request.actorUsername()),
                    request.rejectionReason());

            return toLeaveResponse(saved);
        }

        throw new IllegalArgumentException("Unsupported status: " + status);
    }

    @Override
    @Transactional
    public LeaveResponse reviewLeaveFromMail(Long leaveId, MailLeaveDecisionRequest request) {
        UserProfile actor = userProfileRepository.findByUserNameIgnoreCase(
                        requireNonBlank(request.actorUsername(), "Please login before approving or rejecting leave."))
                .orElseThrow(() -> new IllegalArgumentException("Please login before approving or rejecting leave."));
        if (!actor.isActive()) {
            throw new IllegalArgumentException("Inactive users cannot approve or reject leave requests.");
        }

        LeaveRecord leave = findLeaveOrThrow(leaveId);

        String role = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase();
        String currentStatus = leave.getStatus() == null ? LeaveConstants.STATUS_PENDING : leave.getStatus().toUpperCase();
        String decision = request.decision().trim().toUpperCase();
        String targetStatus;

        if (LeaveConstants.STATUS_PENDING.equals(currentStatus)) {
            validateManagerMailDecision(leaveId, actor, role);
            targetStatus = LeaveConstants.STATUS_APPROVED.equals(decision)
                    ? LeaveConstants.STATUS_MANAGER_APPROVED
                    : LeaveConstants.STATUS_REJECTED;
        } else if (LeaveConstants.STATUS_MANAGER_APPROVED.equals(currentStatus)) {
            if (!LeaveConstants.ROLE_ADMIN.equals(role)) {
                throw new IllegalArgumentException("Only an ADMIN can give final approval or rejection for this leave.");
            }
            targetStatus = LeaveConstants.STATUS_APPROVED.equals(decision)
                    ? LeaveConstants.STATUS_APPROVED
                    : LeaveConstants.STATUS_REJECTED;
        } else {
            throw new IllegalArgumentException("This leave request is already " + currentStatus + " and cannot be changed from email.");
        }

        String rejectionReason = request.rejectionReason();
        if (LeaveConstants.STATUS_REJECTED.equals(targetStatus)
                && (rejectionReason == null || rejectionReason.isBlank())) {
            rejectionReason = "Rejected from email approval link.";
        }

        return updateLeaveStatus(leaveId, new UpdateLeaveStatusRequest(
                actor.getUserName(),
                targetStatus,
                rejectionReason
        ));
    }

    @Override
    @Transactional
    public LeaveResponse applyPartialStatus(Long leaveId, PartialLeaveStatusRequest request) {
        LeaveRecord leave = findLeaveOrThrow(leaveId);

        String currentStatus = leave.getStatus();
        boolean isManagerReview = LeaveConstants.STATUS_PENDING.equalsIgnoreCase(currentStatus);
        boolean isAdminReview   = LeaveConstants.STATUS_MANAGER_APPROVED.equalsIgnoreCase(currentStatus);

        if (!isManagerReview && !isAdminReview) {
            throw new IllegalArgumentException(
                    "Partial review only allowed on PENDING (manager) or MANAGER_APPROVED (admin) leaves. Current: " + currentStatus);
        }

        List<LeaveDate> allDates = leaveDateRepository.findByApplicationId(leaveId);
        String employeeName = leave.getUser() != null && leave.getUser().getFullName() != null
                ? leave.getUser().getFullName() : LeaveConstants.DEFAULT_EMPLOYEE_NAME;

        Map<String, String> decisionMap = new HashMap<>();
        for (PartialLeaveStatusRequest.DateDecision d : request.dateDecisions()) {
            String key = d.date() + "|" + (d.dayType() != null ? d.dayType().toUpperCase() : LeaveConstants.DAY_TYPE_FULL);
            decisionMap.put(key, d.status().toUpperCase());
        }

        List<LeaveDate> approvedDates = new ArrayList<>();
        List<LeaveDate> rejectedDates = new ArrayList<>();
        for (LeaveDate ld : allDates) {
            String key = ld.getLeaveDate() + "|" + (ld.getDayType() != null ? ld.getDayType().toUpperCase() : LeaveConstants.DAY_TYPE_FULL);
            if (LeaveConstants.STATUS_REJECTED.equals(decisionMap.getOrDefault(key, LeaveConstants.STATUS_APPROVED))) {
                rejectedDates.add(ld);
            } else {
                approvedDates.add(ld);
            }
        }

        boolean allRejected = approvedDates.isEmpty();

        String overallStatus = allRejected
                ? LeaveConstants.STATUS_REJECTED
                : (isManagerReview ? LeaveConstants.STATUS_MANAGER_APPROVED : LeaveConstants.STATUS_APPROVED);

        Task activeTask = isManagerReview
                ? findTaskByDefinitionKey(leaveId, "task_manager_approval")
                : findTaskByDefinitionKey(leaveId, "task_admin_approval");

        if (activeTask != null) {
            if (!allRejected && !rejectedDates.isEmpty()) {
                leaveDateRepository.deleteAllById(rejectedDates.stream().map(LeaveDate::getId).toList());
                // Immediately notify employee about rejected dates
                leaveEmailService.sendLeaveStatusNotification(
                        resolveStatusRecipients(leave), employeeName, leave.getLeaveType(),
                        rejectedDates, leave.getReason(), LeaveConstants.STATUS_REJECTED,
                        resolveActorDisplayName(request.actorUsername()),
                        resolveActorRoleDisplay(request.actorUsername()),
                        request.rejectionReason());
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("actorUsername", request.actorUsername());
            vars.put("status", overallStatus);
            if (LeaveConstants.STATUS_REJECTED.equals(overallStatus)) {
                vars.put("rejectionReason", request.rejectionReason());
            }

            String customNote;
            if (isManagerReview) {
                if (allRejected) {
                    customNote = "All dates rejected by " + resolveActorDisplayName(request.actorUsername())
                            + (request.rejectionReason() != null && !request.rejectionReason().isBlank() ? ": " + request.rejectionReason() : "");
                } else {
                    customNote = resolveActorDisplayName(request.actorUsername()) + " approved " + approvedDates.size()
                            + " date(s)" + (rejectedDates.isEmpty() ? "" : ", removed " + rejectedDates.size() + " rejected date(s)")
                            + ". Pending admin final approval.";
                }
            } else {
                customNote = "Admin final decision: " + approvedDates.size() + " approved, " + rejectedDates.size() + " rejected"
                        + (request.rejectionReason() != null && !request.rejectionReason().isBlank() ? ": " + request.rejectionReason() : "");
            }
            vars.put("customNote", customNote);

            if (!rejectedDates.isEmpty()) {
                try {
                    List<LeaveDateDto> approvedDtos = approvedDates.stream().map(d -> new LeaveDateDto(d.getLeaveDate(), d.getDayType())).toList();
                    vars.put("leaveDates", OBJECT_MAPPER.writeValueAsString(approvedDtos));
                } catch (Exception e) {
                    log.warn("Failed to serialize updated leaveDates for flowable", e);
                }
            }

            taskService.complete(activeTask.getId(), vars);
            log.info("[LeaveService] Task {} completed ({}) via partial approval for leaveId={}", activeTask.getId(), overallStatus, leaveId);

            return toLeaveResponse(findLeaveOrThrow(leaveId));
        }

        if (isManagerReview) {
            if (allRejected) {
                leave.setManagerRejected(request.actorUsername(), request.rejectionReason());
                leave.appendTrailEntry(LeaveConstants.STATUS_REJECTED, request.actorUsername(), null, null,
                        "All dates rejected by " + resolveActorDisplayName(request.actorUsername())
                        + (request.rejectionReason() != null && !request.rejectionReason().isBlank() ? ": " + request.rejectionReason() : ""));
                LeaveRecord saved = leaveRepository.save(leave);
                leaveEmailService.sendLeaveStatusNotification(
                        resolveStatusRecipients(saved), employeeName, saved.getLeaveType(),
                        rejectedDates, saved.getReason(), LeaveConstants.STATUS_REJECTED,
                        resolveActorDisplayName(request.actorUsername()),
                        resolveActorRoleDisplay(request.actorUsername()), request.rejectionReason());
                return toLeaveResponse(saved);
            }

            if (!rejectedDates.isEmpty()) {
                leaveDateRepository.deleteAllById(rejectedDates.stream().map(LeaveDate::getId).toList());
            }
            leave.setManagerApproved(request.actorUsername());
            leave.appendTrailEntry(LeaveConstants.STATUS_MANAGER_APPROVED, request.actorUsername(), null, null,
                    resolveActorDisplayName(request.actorUsername()) + " approved " + approvedDates.size()
                    + " date(s)" + (rejectedDates.isEmpty() ? "" : ", removed " + rejectedDates.size() + " rejected date(s)")
                    + ".");
            LeaveRecord saved = leaveRepository.save(leave);

            List<LeaveDate> remainingDates = leaveDateRepository.findByApplicationId(leaveId);
            List<String> adminEmails = userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN).stream()
                    .map(UserProfile::getEmailId).filter(e -> e != null && !e.isBlank()).toList();
            String managerDisplayName = resolveActorDisplayName(request.actorUsername());

            leaveEmailService.sendManagerApprovedPendingAdminNotification(
                    adminEmails, resolveStatusRecipients(saved), employeeName,
                    saved.getLeaveType(), remainingDates, saved.getReason(), managerDisplayName, leaveId);

            if (!rejectedDates.isEmpty()) {
                leaveEmailService.sendLeaveStatusNotification(
                        resolveStatusRecipients(saved), employeeName, saved.getLeaveType(),
                        rejectedDates, saved.getReason(), LeaveConstants.STATUS_REJECTED,
                        managerDisplayName, resolveActorRoleDisplay(request.actorUsername()),
                        request.rejectionReason());
            }

            return toLeaveResponse(saved);

        } else {

            if (!allRejected && !rejectedDates.isEmpty()) {
                leaveDateRepository.deleteAllById(rejectedDates.stream().map(LeaveDate::getId).toList());
            }
            if (LeaveConstants.STATUS_APPROVED.equals(overallStatus)) {
                leave.setAdminApproved(request.actorUsername());
            } else {
                leave.setAdminRejected(request.actorUsername(), request.rejectionReason());
            }
            leave.appendTrailEntry(overallStatus, request.actorUsername(), null, null,
                    "Admin final decision: " + approvedDates.size() + " approved, " + rejectedDates.size() + " rejected"
                    + (request.rejectionReason() != null && !request.rejectionReason().isBlank() ? ": " + request.rejectionReason() : ""));
            LeaveRecord saved = leaveRepository.save(leave);

            if (!approvedDates.isEmpty()) {
                final Long userId = leave.getUserId();
                final Integer leaveTypeId = leave.getLeaveTypeId();
                double days = approvedDates.stream()
                        .mapToDouble(d -> d.getDayType() != null
                                && d.getDayType().contains(LeaveConstants.DAY_TYPE_HALF_KEYWORD) ? 0.5 : 1.0)
                        .sum();
                CompletableFuture.runAsync(
                        () -> leaveBalanceService.deductLeaveBalance(userId, leaveTypeId, days),
                        leaveTaskExecutor
                ).exceptionally(ex -> {
                    log.error("[LeaveService] Failed to deduct balance for userId={}: {}", userId, ex.getMessage());
                    return null;
                });
            }

            leaveEmailService.sendPartialLeaveStatusNotification(
                    resolveStatusRecipients(saved), employeeName, saved.getLeaveType(),
                    approvedDates, rejectedDates, saved.getReason(),
                    resolveActorDisplayName(request.actorUsername()),
                    resolveActorRoleDisplay(request.actorUsername()), request.rejectionReason());
            return toLeaveResponse(saved);
        }
    }

    @Override
    @Transactional
    public LeaveResponse updateLeave(Long leaveId, UpdateLeaveRequest request) {
        LeaveRecord leave = findLeaveOrThrow(leaveId);

        if (!LeaveConstants.STATUS_PENDING.equalsIgnoreCase(leave.getStatus())) {
            throw new IllegalArgumentException("Only PENDING leave applications can be edited.");
        }
        if (request.leaveDates() == null || request.leaveDates().isEmpty()) {
            throw new IllegalArgumentException("At least one leave date is required.");
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave type not found with id: " + request.leaveTypeId()));

        validateRequestedLeaveBalance(leave.getUser(), leaveType, request.leaveDates());

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
        if (!LeaveConstants.STATUS_PENDING.equalsIgnoreCase(leave.getStatus())) {
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
        List<UserProfile> candidates = userProfileRepository.findAllActiveExcept(requestingUser.getId());
        return candidates.stream()
                .map(u -> new NotifyUserResponse(u.getId(), u.getFullName(), u.getEmailId(), u.getRole()))
                .toList();
    }

    @Override
    public List<String> getBookedDates(String username) {
        String normalized = requireNonBlank(username, "Username is required.");
        return leaveRepository.findBookedDatesByUsername(normalized);
    }

    @Override
    public List<AuditTrailEntryDto> getAuditTrail(Long leaveId) {
        LeaveRecord leave = findLeaveOrThrow(leaveId);
        String trail = leave.getTrail();
        if (trail == null || trail.isBlank() || trail.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = OBJECT_MAPPER.readValue(trail, new TypeReference<>() {});
            return raw.stream().map(entry -> new AuditTrailEntryDto(
                    stringValue(entry.get("event")),
                    stringValue(entry.get("actor")),
                    stringValue(entry.get("timestamp")),
                    stringValue(entry.get("processInstanceId")),
                    stringValue(entry.get("taskId")),
                    stringValue(entry.get("note"))
            )).toList();
        } catch (Exception e) {
            log.warn("[getAuditTrail] Failed to parse trail for leaveId={}: {}", leaveId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public LeaveResponse appendAuditTrailEntry(Long leaveId, AppendAuditTrailRequest request) {
        LeaveRecord leave = findLeaveOrThrow(leaveId);
        leave.appendTrailEntry(
                request.event(),
                request.actor(),
                request.processInstanceId(),
                request.taskId(),
                request.note()
        );
        return toLeaveResponse(leaveRepository.save(leave));
    }

    @Override
    @Transactional
    public void resolveApprover(DelegateExecution execution) {
        Long userId = longValue(execution.getVariable("userId"));
        String username = stringValue(execution.getVariable("username"));
        Long leaveId = longValue(execution.getVariable("leaveId"));

        UserProfile user = resolveFlowableUser(userId, username);
        if (user == null) {
            log.warn("[ResolveApprover] Cannot resolve user userId={} username={}", userId, username);
            execution.setVariable("managerUsername", LeaveConstants.DEFAULT_ADMIN_USERNAME);
            execution.setVariable("managerEmail", "");
            execution.setVariable("adminEmail", "");
            execution.setVariable("employeeName", username != null ? username : LeaveConstants.DEFAULT_EMPLOYEE_NAME);
            return;
        }

        String managerUsername = null;
        String managerEmail = null;
        String createdBy = user.getCreatedBy();
        if (createdBy != null && !createdBy.isBlank()) {
            UserProfile manager = userProfileRepository.findByUserNameIgnoreCase(createdBy)
                    .filter(UserProfile::isActive)
                    .orElse(null);
            if (manager != null) {
                managerUsername = manager.getUserName();
                managerEmail = manager.getEmailId();
            }
        }

        String adminEmail = userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN).stream()
                .map(UserProfile::getEmailId)
                .filter(email -> email != null && !email.isBlank())
                .findFirst()
                .orElse("");

        if (managerUsername == null || managerUsername.isBlank()) {
            managerUsername = userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN).stream()
                    .map(UserProfile::getUserName)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(LeaveConstants.DEFAULT_ADMIN_USERNAME);
            managerEmail = adminEmail;
        }

        execution.setVariable("managerUsername", managerUsername);
        execution.setVariable("managerEmail", managerEmail != null ? managerEmail : "");
        execution.setVariable("adminEmail", adminEmail);

        // Resolve employee display name: prefer fullName, fall back to userName, then username variable
        String employeeDisplayName = user.getFullName();
        if (employeeDisplayName == null || employeeDisplayName.isBlank()) {
            employeeDisplayName = user.getUserName();
        }
        if (employeeDisplayName == null || employeeDisplayName.isBlank()) {
            employeeDisplayName = username != null ? username : LeaveConstants.DEFAULT_EMPLOYEE_NAME;
        }
        execution.setVariable("employeeName", employeeDisplayName);

        final String finalEmployeeDisplayName = employeeDisplayName;
        final String finalManagerEmail = managerEmail;
        final String finalManagerUsername = managerUsername;
        if (leaveId != null) {
            leaveRepository.findDetailedById(leaveId).ifPresent(leave -> {
                List<LeaveDate> dates = leaveDateRepository.findByApplicationId(leaveId);
                List<String> recipients = buildRecipients(finalManagerEmail, adminEmail);
                if (!recipients.isEmpty()) {
                    // Resolve manager's display name and role for the email
                    String managerDisplayName = userProfileRepository.findByUserName(finalManagerUsername)
                            .map(m -> m.getFullName() != null && !m.getFullName().isBlank()
                                    ? m.getFullName() : finalManagerUsername)
                            .orElse(finalManagerUsername);
                    String managerDisplayRole = userProfileRepository.findByUserName(finalManagerUsername)
                            .map(m -> resolveRoleDisplay(m.getRole()))
                            .orElse("");
                    String employeeDisplayRole = resolveRoleDisplay(user.getRole());
                    leaveEmailService.sendPendingApprovalReminder(
                            recipients,
                            finalEmployeeDisplayName,
                            employeeDisplayRole,
                            leave.getLeaveType(),
                            dates,
                            leave.getReason(),
                            managerDisplayName,
                            managerDisplayRole,
                            loginUrl,
                            leaveId
                    );
                }
                execution.setVariable("leaveType", leave.getLeaveType());
                leave.appendTrailEntry(
                        LeaveConstants.EVENT_APPROVER_RESOLVED,
                        LeaveConstants.SYSTEM_ACTOR,
                        execution.getProcessInstanceId(),
                        null,
                        "Assigned to approver: " + finalManagerUsername
                );
                leaveRepository.save(leave);
            });
        }

        log.info("[ResolveApprover] leaveId={} approver={}", leaveId, managerUsername);
    }

    @Override
    public void calculateReminderSchedule(DelegateExecution execution) {
        String leaveDatesJson = stringValue(execution.getVariable("leaveDates"));
        LocalDate earliest = parseEarliestFutureDate(leaveDatesJson);

        ZoneId zone = ZoneId.systemDefault();
        Date never = toDate(LocalDate.now().plusYears(100).atTime(8, 0).atZone(zone));

        if (earliest == null) {
            log.warn("[CalculateReminderSchedule] No future leave dates found, disabling reminder timers.");
            execution.setVariable("fourDayReminderTime", never);
            execution.setVariable("twoDayReminderTime", never);
            return;
        }

        Date now = new Date();

        Date fourDayTime = toDate(earliest.minusDays(4).atTime(8, 0).atZone(zone));
        if (fourDayTime.before(now)) {
            log.info("[CalculateReminderSchedule] 4-day reminder time already past, disabling.");
            fourDayTime = never;
        }

        Date twoDayTime = toDate(earliest.minusDays(2).atTime(8, 0).atZone(zone));
        if (twoDayTime.before(now)) {
            log.info("[CalculateReminderSchedule] 2-day reminder time already past, disabling.");
            twoDayTime = never;
        }

        execution.setVariable("fourDayReminderTime", fourDayTime);
        execution.setVariable("twoDayReminderTime", twoDayTime);

        log.info("[CalculateReminderSchedule] earliestLeaveDate={}, 4dayTimer={}, 2dayTimer={}",
                earliest, fourDayTime, twoDayTime);
    }

    @Override
    public void sendReminderEmail(DelegateExecution execution) {
        String reminderType = stringValue(execution.getVariable("reminderType"));
        if (reminderType == null || reminderType.isBlank()) {
            log.warn("[SendReminderEmail] reminderType variable missing, skipping.");
            return;
        }
        String managerEmail = stringValue(execution.getVariable("managerEmail"));
        String adminEmail = stringValue(execution.getVariable("adminEmail"));
        String employeeName = stringValue(execution.getVariable("employeeName"));
        if (employeeName == null || employeeName.isBlank()) {
            employeeName = stringValue(execution.getVariable("username"));
        }
        if (employeeName == null || employeeName.isBlank()) {
            employeeName = LeaveConstants.DEFAULT_EMPLOYEE_NAME;
        }

        Long leaveId = longValue(execution.getVariable("leaveId"));
        String leaveType = stringValue(execution.getVariable("leaveType"));
        String reason = stringValue(execution.getVariable("reason"));

        boolean sent = leaveReminderDispatchService.dispatchReminder(
                leaveId,
                reminderType,
                managerEmail != null ? managerEmail : "",
                adminEmail != null ? adminEmail : "",
                employeeName,
                leaveType,
                reason != null ? reason : "",
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId()
        );
        if (!sent) {
            log.debug("[SendReminderEmail] Skipped {} reminder for leaveId={}", reminderType, leaveId);
        }
    }

    @Override
    @Transactional
    public void updateLeaveStatusFromFlowable(DelegateExecution execution) {
        String status = stringValue(execution.getVariable("status"));
        if (status == null || status.isBlank()) {
            log.warn("[UpdateLeaveStatus] status variable missing, skipping.");
            return;
        }
        Long leaveId = longValue(execution.getVariable("leaveId"));
        String actor = stringValue(execution.getVariable("actorUsername"));
        String rejectionReason = stringValue(execution.getVariable("rejectionReason"));

        if (leaveId == null) {
            log.warn("[UpdateLeaveStatus] Missing leaveId, skipping.");
            return;
        }

        LeaveRecord leave = findLeaveOrThrow(leaveId);

        String currentStatus = leave.getStatus() != null ? leave.getStatus() : LeaveConstants.STATUS_PENDING;
        if (LeaveConstants.STATUS_MANAGER_APPROVED.equals(status)) {
            leave.setManagerApproved(actor);
        } else if (LeaveConstants.STATUS_APPROVED.equals(status)) {
            leave.setAdminApproved(actor);
        } else if (LeaveConstants.STATUS_REJECTED.equals(status)) {
            if (LeaveConstants.STATUS_MANAGER_APPROVED.equalsIgnoreCase(currentStatus)) {
                leave.setAdminRejected(actor, rejectionReason);
            } else {
                leave.setManagerRejected(actor, rejectionReason);
            }
        }

        String customNote = stringValue(execution.getVariable("customNote"));
        String note;
        if (customNote != null && !customNote.isBlank()) {
            note = customNote;
        } else {
            note = LeaveConstants.STATUS_REJECTED.equals(status) && rejectionReason != null && !rejectionReason.isBlank()
                    ? "Rejected by " + resolveActorDisplayName(actor) + ": " + rejectionReason
                    : status + " by " + resolveActorDisplayName(actor);
        }
        leave.appendTrailEntry(
                status,
                actor != null ? actor : LeaveConstants.SYSTEM_ACTOR,
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId(),
                note
        );
        leaveRepository.save(leave);

        if (leave.getUser() != null && leave.getUser().getFullName() != null) {
            execution.setVariable("employeeName", leave.getUser().getFullName());
        }
        execution.setVariable("leaveType", leave.getLeaveType());

        log.info("[UpdateLeaveStatus] leaveId={} status={} actor={}", leaveId, status, actor);
    }

    @Override
    public void notifyAdminForFinalApproval(DelegateExecution execution) {
        Long leaveId = longValue(execution.getVariable("leaveId"));
        String managerUsername = stringValue(execution.getVariable("actorUsername"));
        String employeeName = stringValue(execution.getVariable("employeeName"));
        if (employeeName == null || employeeName.isBlank()) employeeName = LeaveConstants.DEFAULT_EMPLOYEE_NAME;

        List<String> adminEmails = userProfileRepository.findActiveByRole(LeaveConstants.ROLE_ADMIN).stream()
                .map(UserProfile::getEmailId)
                .filter(e -> e != null && !e.isBlank())
                .toList();

        if (leaveId == null || adminEmails.isEmpty()) {
            log.warn("[NotifyAdminForFinalApproval] Missing leaveId or no admin emails, skipping.");
            return;
        }

        LeaveRecord leave = leaveRepository.findDetailedById(leaveId).orElse(null);
        if (leave == null) return;

        List<LeaveDate> dates = leaveDateRepository.findByApplicationId(leaveId);
        List<String> employeeEmails = resolveStatusRecipients(leave);
        String managerDisplayName = resolveActorDisplayName(managerUsername);

        leaveEmailService.sendManagerApprovedPendingAdminNotification(
                adminEmails, employeeEmails, employeeName,
                leave.getLeaveType(), dates, leave.getReason(), managerDisplayName, leaveId
        );
        log.info("[NotifyAdminForFinalApproval] leaveId={} notified {} admin(s)", leaveId, adminEmails.size());
    }

    @Override
    public void sendLeaveStatusMail(DelegateExecution execution) {
        Long leaveId = longValue(execution.getVariable("leaveId"));
        String status = stringValue(execution.getVariable("status"));
        String employeeName = stringValue(execution.getVariable("employeeName"));
        if (employeeName == null || employeeName.isBlank()) {
            employeeName = LeaveConstants.DEFAULT_EMPLOYEE_NAME;
        }
        String leaveType = stringValue(execution.getVariable("leaveType"));
        String reason = stringValue(execution.getVariable("reason"));
        String actionBy = stringValue(execution.getVariable("actorUsername"));
        String rejectionReason = stringValue(execution.getVariable("rejectionReason"));

        List<LeaveDate> dates = leaveId != null ? leaveDateRepository.findByApplicationId(leaveId) : List.of();
        List<String> recipients = leaveId != null
                ? leaveRepository.findDetailedById(leaveId).map(this::resolveStatusRecipients).orElse(List.of())
                : List.of();
        if (recipients.isEmpty()) {
            log.warn("[SendLeaveStatusMail] No recipients leaveId={} status={}", leaveId, status);
            return;
        }

        log.info("[SendLeaveStatusMail] Sending {} mail leaveId={} to {} recipient(s)",
                status, leaveId, recipients.size());
        String actorUsername = actionBy != null ? actionBy : LeaveConstants.SYSTEM_DISPLAY_NAME;
        leaveEmailService.sendLeaveStatusNotification(
                recipients,
                employeeName,
                leaveType != null ? leaveType : "",
                dates,
                reason != null ? reason : "",
                status != null ? status : "",
                resolveActorDisplayName(actorUsername),
                resolveActorRoleDisplay(actorUsername),
                rejectionReason
        );
    }

    @Override
    @Transactional
    public void deductLeaveBalance(DelegateExecution execution) {
        Long userId = longValue(execution.getVariable("userId"));
        Integer leaveTypeId = integerValue(execution.getVariable("leaveTypeId"));

        if (userId == null || leaveTypeId == null) {
            log.warn("[DeductLeaveBalance] Missing userId or leaveTypeId, skipping.");
            return;
        }

        String leaveUniqueName = leaveTypeRepository.findUniqueNameById(leaveTypeId);
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) {
            log.warn("[DeductLeaveBalance] No leaveUniqueName for leaveTypeId={}, skipping.", leaveTypeId);
            return;
        }

        Long leaveId = longValue(execution.getVariable("leaveId"));
        if (leaveId == null) {
            log.warn("[DeductLeaveBalance] leaveId is null, skipping.");
            return;
        }

        List<LeaveDate> dates = leaveDateRepository.findByApplicationId(leaveId);
        double days = dates.stream()
                .mapToDouble(date -> date.getDayType() != null
                        && date.getDayType().toUpperCase().contains(LeaveConstants.DAY_TYPE_HALF_KEYWORD) ? 0.5 : 1.0)
                .sum();

        if (days <= 0) {
            log.warn("[DeductLeaveBalance] 0 days calculated for leaveId={}, skipping.", leaveId);
            return;
        }

        int rows = employeeLeaveRepository.deductLeaveBalance(userId, leaveUniqueName, days);
        log.info("[DeductLeaveBalance] Deducted {} days of '{}' for userId={} rows={}",
                days, leaveUniqueName, userId, rows);
    }


    private void startApprovalProcess(LeaveRecord saved, UserProfile user, CreateLeaveRequest request) {
        try {
            String leaveDatesJson = OBJECT_MAPPER.writeValueAsString(
                    request.leaveDates().stream()
                            .map(d -> Map.of("date", d.date().toString(), "dayType", d.dayType() != null ? d.dayType() : LeaveConstants.DAY_TYPE_FULL))
                            .toList()
            );
            String notifyUserIdsStr = request.notifyUserIds() != null
                    ? request.notifyUserIds().stream().map(String::valueOf).reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b)
                    : "";

            Map<String, Object> vars = new HashMap<>();
            vars.put("leaveId", saved.getId());
            vars.put("userId", user.getId());
            vars.put("username", user.getUserName());
            vars.put("leaveTypeId", saved.getLeaveTypeId() != null ? saved.getLeaveTypeId().longValue() : null);
            vars.put("leaveDates", leaveDatesJson);
            vars.put("reason", saved.getReason());
            vars.put("comments", saved.getComments());
            vars.put("trail", saved.getTrail());
            vars.put("editable", saved.isEditable());
            vars.put("notifyUserIds", notifyUserIdsStr);

            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    PROCESS_DEF_KEY, String.valueOf(saved.getId()), vars
            );
            if (instance == null) {
                log.warn("[LeaveService] Flowable returned null process instance for leaveId={}", saved.getId());
                return;
            }
            saved.appendTrailEntry(
                "PROCESS_STARTED",
                user.getUserName(),
                instance.getId(),
                null,
                "Flowable approval process started"
            );
            leaveRepository.save(saved);

            log.info("[LeaveService] Started process {} for leaveId={}", instance.getId(), saved.getId());
        } catch (JsonProcessingException e) {
            log.error("[LeaveService] Failed to serialize leaveDates for leaveId={}: {}", saved.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("[LeaveService] Failed to start approval process for leaveId={}: {}", saved.getId(), e.getMessage());
        }
    }

    private Task findTaskByDefinitionKey(Long leaveId, String taskDefinitionKey) {
        try {
            if (taskService == null) {
                return null;
            }

            TaskQuery taskQuery = taskService.createTaskQuery();
            if (taskQuery == null) {
                return null;
            }

            return taskQuery
                    .processInstanceBusinessKey(String.valueOf(leaveId))
                    .taskDefinitionKey(taskDefinitionKey)
                    .singleResult();
        } catch (Exception e) {
            log.warn("[LeaveService] Could not query Flowable task '{}' for leaveId={}: {}",
                    taskDefinitionKey, leaveId, e.getMessage());
            return null;
        }
    }

    private void validateManagerMailDecision(Long leaveId, UserProfile actor, String role) {
        if (!LeaveConstants.ROLE_MANAGER.equals(role) && !LeaveConstants.ROLE_ADMIN.equals(role)) {
            throw new IllegalArgumentException("Only a MANAGER or ADMIN can approve or reject this pending leave.");
        }

        if (LeaveConstants.ROLE_ADMIN.equals(role)) {
            return;
        }

        Task managerTask = findTaskByDefinitionKey(leaveId, "task_manager_approval");
        if (managerTask == null) {
            return;
        }

        String assignee = managerTask.getAssignee();
        if (assignee != null && !assignee.isBlank()
                && !assignee.equalsIgnoreCase(actor.getUserName())) {
            throw new IllegalArgumentException("This leave request is assigned to " + assignee + ", not " + actor.getUserName() + ".");
        }
    }

    private void saveNotifyUsers(LeaveRecord saved, Long ownerId, List<Long> notifyUserIds) {
        if (notifyUserIds == null || notifyUserIds.isEmpty()) return;
        List<LeaveNotifyUser> entries = notifyUserIds.stream()
                .distinct()
                .filter(uid -> !uid.equals(ownerId))
                .map(uid -> userProfileRepository.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .map(u -> new LeaveNotifyUser(saved, u))
                .toList();
        if (!entries.isEmpty()) leaveNotifyUserRepository.saveAll(entries);
    }

    private List<String> resolveStatusRecipients(LeaveRecord leave) {
        Set<String> recipients = new LinkedHashSet<>();

        if (leave.getEmailId() != null && !leave.getEmailId().isBlank()) {
            recipients.add(leave.getEmailId().trim());
        }

        leaveNotifyUserRepository.findByLeaveId(leave.getId()).stream()
                .map(LeaveNotifyUser::getUserEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .forEach(recipients::add);

        return new ArrayList<>(recipients);
    }


    private UserProfile resolveFlowableUser(Long userId, String username) {
        if (userId != null) {
            return userProfileRepository.findById(userId).orElse(null);
        }
        if (username != null && !username.isBlank()) {
            return userProfileRepository.findByUserName(username.trim()).orElse(null);
        }
        return null;
    }

    private List<String> buildRecipients(String managerEmail, String adminEmail) {
        Set<String> recipients = new LinkedHashSet<>();
        if (managerEmail != null && !managerEmail.isBlank()) {
            recipients.add(managerEmail.trim());
        }
        if (adminEmail != null && !adminEmail.isBlank()) {
            recipients.add(adminEmail.trim());
        }
        return new ArrayList<>(recipients);
    }

    private LocalDate parseEarliestFutureDate(String leaveDatesJson) {
        if (leaveDatesJson == null || leaveDatesJson.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> dates = OBJECT_MAPPER.readValue(leaveDatesJson, new TypeReference<>() {});
            return dates.stream()
                    .map(values -> {
                        Object date = values.get("date");
                        if (date == null) return null;
                        try {
                            return LocalDate.parse(date.toString(), DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (Exception ignored) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[CalculateReminderSchedule] Failed to parse leaveDates JSON: {}", e.getMessage());
            return null;
        }
    }

    private Date toDate(ZonedDateTime dateTime) {
        return Date.from(dateTime.toInstant());
    }

    private Long longValue(Object value) {
        if (value instanceof Long longValue) return longValue;
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String stringValue && !stringValue.isBlank()) return Long.parseLong(stringValue);
        return null;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Integer integerValue) return integerValue;
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String stringValue && !stringValue.isBlank()) return Integer.parseInt(stringValue);
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
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

    private void validateRequestedLeaveBalance(UserProfile user, LeaveType leaveType, List<LeaveDateDto> leaveDates) {
        String leaveUniqueName = leaveType.getLeaveUniqueName();
        if (leaveUniqueName == null || leaveUniqueName.isBlank()) {
            return;
        }

        Double remaining = employeeLeaveRepository.getRemainingBalance(user.getId(), leaveUniqueName);
        double requestedDays = calculateRequestedLeaveDays(leaveDates);
        if (remaining != null && remaining <= 0) {
            throw new IllegalArgumentException(
                    "You have no remaining " + leaveType.getLeaveName() + " balance.");
        }
        if (remaining != null && requestedDays > remaining) {
            throw new IllegalArgumentException(String.format(
                    "Requested %.1f day(s) exceeds remaining %s balance of %.1f day(s).",
                    requestedDays, leaveType.getLeaveName(), remaining));
        }
    }

    private double calculateRequestedLeaveDays(List<LeaveDateDto> leaveDates) {
        return leaveDates.stream()
                .mapToDouble(date -> date.dayType() != null
                        && date.dayType().toUpperCase().contains(LeaveConstants.DAY_TYPE_HALF_KEYWORD) ? 0.5 : 1.0)
                .sum();
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
        String status = leave.getStatus() != null ? leave.getStatus() : LeaveConstants.STATUS_PENDING;
        String approvedBy = leave.getApprovedBy();
        String managerApprovedBy = leave.getManagerApprovedBy();
        OffsetDateTime managerApprovedAt = leave.getManagerApprovedAt();
        String managerRejectedBy = leave.getManagerRejectedBy();
        OffsetDateTime managerRejectedAt = leave.getManagerRejectedAt();
        String adminApprovedBy = leave.getAdminApprovedBy();
        String adminRejectedBy = leave.getAdminRejectedBy();
        OffsetDateTime adminApprovedAt = leave.getAdminApprovedAt();
        OffsetDateTime adminRejectedAt = leave.getAdminRejectedAt();

        if (LeaveConstants.STATUS_MANAGER_APPROVED.equals(status)) {
            if ((managerApprovedBy == null || managerApprovedBy.isBlank()) && approvedBy != null && !approvedBy.isBlank()) {
                managerApprovedBy = approvedBy;
            }
            adminApprovedBy = null;
            adminRejectedBy = null;
            adminApprovedAt = null;
            adminRejectedAt = null;
        } else if (LeaveConstants.STATUS_APPROVED.equals(status)) {
            if ((adminApprovedBy == null || adminApprovedBy.isBlank()) && approvedBy != null && !approvedBy.isBlank()) {
                adminApprovedBy = approvedBy;
            }
        } else if (LeaveConstants.STATUS_REJECTED.equals(status) && managerRejectedBy != null && !managerRejectedBy.isBlank()) {
            adminApprovedBy = null;
            adminRejectedBy = null;
            adminApprovedAt = null;
            adminRejectedAt = null;
        }

        return new LeaveResponse(
                leave.getId(), leave.getUserId(),
                user == null ? null : user.getFullName(),
                leave.getEmailId(), leave.getLeaveTypeId(), leave.getLeaveType(),
                dates, leave.getReason(), leave.getComments(), leave.getTrail(),
                leave.isEditable(), status,
                approvedBy, managerApprovedBy, managerRejectedBy,
                managerApprovedAt, managerRejectedAt,
                adminApprovedBy, adminRejectedBy,
                adminApprovedAt, adminRejectedAt,
                leave.getRejectionReason(),
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

    private LeaveRecord findLeaveOrThrow(Long leaveId) {
        return findLeaveOrThrow(leaveId);
    }

    private String requireNonBlank(String value, String message) {
        return StringUtils.requireNonBlank(value, message);
    }

    private String trimOrNull(String value) {
        return StringUtils.trimOrNull(value);
    }

    private record ActorInfo(String displayName, String roleDisplay) {}

    private ActorInfo resolveActorInfo(String username) {
        if (username == null || username.isBlank())
            return new ActorInfo(LeaveConstants.SYSTEM_DISPLAY_NAME, "");
        return userProfileRepository.findByUserName(username)
                .map(u -> new ActorInfo(
                        u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : username,
                        resolveRoleDisplay(u.getRole())))
                .orElse(new ActorInfo(username, ""));
    }

    private String resolveActorDisplayName(String username) {
        return resolveActorInfo(username).displayName();
    }

    private String resolveActorRoleDisplay(String username) {
        return resolveActorInfo(username).roleDisplay();
    }

    private String resolveRoleDisplay(String role) {
        if (role == null || role.isBlank()) return "";
        return switch (role.toUpperCase()) {
            case LeaveConstants.ROLE_ADMIN    -> "Administrator";
            case LeaveConstants.ROLE_MANAGER  -> "Manager";
            case LeaveConstants.ROLE_EMPLOYEE -> "Employee";
            default -> role.charAt(0) + role.substring(1).toLowerCase();
        };
    }

    private String normalizeUniqueName(String value) {
        return StringUtils.requireNonBlank(value, "Leave unique name is required").replace(' ', '_').toUpperCase();
    }

    private String normalizeGender(String value) {
        String upper = StringUtils.trimOrNull(value);
        if (upper == null) return null;
        upper = upper.toUpperCase();
        return (upper.equals(LeaveConstants.GENDER_MALE) || upper.equals(LeaveConstants.GENDER_FEMALE)) ? upper : null;
    }
}
