package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.PartialLeaveStatusRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.dto.NotifyUserResponse;
import com.cresensolutions.leaveservice.dto.PagedResponse;
import com.cresensolutions.leaveservice.dto.UpdateLeaveRequest;
import com.cresensolutions.leaveservice.dto.UpdateLeaveStatusRequest;
import com.cresensolutions.leaveservice.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    public LeaveResponse createLeave(@Valid @RequestBody CreateLeaveRequest request) {
        return leaveService.createLeave(request);
    }

    @PostMapping("/applications")
    public LeaveResponse submitLeaveApplication(@Valid @RequestBody CreateLeaveRequest request) {
        return leaveService.createLeave(request);
    }

    @GetMapping
    public PagedResponse<LeaveResponse> getAllLeaves(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return PagedResponse.from(leaveService.getAllLeaves(page, size));
    }

    @GetMapping("/{leaveId}")
    public LeaveResponse getLeaveById(@PathVariable Long leaveId) {
        return leaveService.getLeaveById(leaveId);
    }

    @GetMapping("/user/{userId}")
    public PagedResponse<LeaveResponse> getLeavesByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return PagedResponse.from(leaveService.getLeavesByUserId(userId, page, size));
    }

    @GetMapping("/by-username/{username}")
    public PagedResponse<LeaveResponse> getLeavesByUsername(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        return PagedResponse.from(leaveService.getLeavesByUsername(username, page, size));
    }

    @GetMapping("/history/{username}")
    public PagedResponse<LeaveResponse> getLeaveHistoryGrid(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        return PagedResponse.from(leaveService.getLeavesByUsername(username, page, size));
    }

    @GetMapping("/by-manager/{managerUsername}")
    public PagedResponse<LeaveResponse> getLeavesByManagerUsername(
            @PathVariable String managerUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        return PagedResponse.from(leaveService.getLeavesByManagerUsername(managerUsername, page, size));
    }

    @PutMapping("/{leaveId}/status")
    public LeaveResponse updateLeaveStatus(
            @PathVariable Long leaveId,
            @Valid @RequestBody UpdateLeaveStatusRequest request
    ) {
        return leaveService.updateLeaveStatus(leaveId, request);
    }

    @PutMapping("/{leaveId}/partial-status")
    public LeaveResponse applyPartialStatus(
            @PathVariable Long leaveId,
            @Valid @RequestBody PartialLeaveStatusRequest request
    ) {
        return leaveService.applyPartialStatus(leaveId, request);
    }

    @PutMapping("/{leaveId}")
    public LeaveResponse updateLeave(
            @PathVariable Long leaveId,
            @Valid @RequestBody UpdateLeaveRequest request
    ) {
        return leaveService.updateLeave(leaveId, request);
    }

    @DeleteMapping("/{leaveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePendingLeave(@PathVariable Long leaveId) {
        leaveService.deletePendingLeave(leaveId);
    }

    @GetMapping("/types")
    public List<LeaveTypeResponse> getLeaveTypes() {
        return leaveService.getLeaveTypes();
    }

    @PostMapping("/types")
    public LeaveTypeResponse createLeaveType(@Valid @RequestBody CreateLeaveTypeRequest request) {
        return leaveService.createLeaveType(request);
    }

    @PutMapping("/types/{leaveTypeId}")
    public LeaveTypeResponse updateLeaveType(
            @PathVariable Integer leaveTypeId,
            @Valid @RequestBody CreateLeaveTypeRequest request
    ) {
        return leaveService.updateLeaveType(leaveTypeId, request);
    }

    @DeleteMapping("/types/{leaveTypeId}")
    public void deleteLeaveType(@PathVariable Integer leaveTypeId) {
        leaveService.deleteLeaveType(leaveTypeId);
    }
    
    @GetMapping("/notify-users")
    public List<NotifyUserResponse> getNotifyUsers(@RequestParam String username) {
        return leaveService.getNotifyUsers(username);
    }

    @GetMapping("/booked-dates/{username}")
    public List<String> getBookedDates(@PathVariable String username) {
        return leaveService.getBookedDates(username);
    }
}
