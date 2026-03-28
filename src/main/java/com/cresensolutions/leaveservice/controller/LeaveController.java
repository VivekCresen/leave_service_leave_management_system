package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<LeaveResponse> getAllLeaves() {
        return leaveService.getAllLeaves();
    }

    @GetMapping("/{leaveId}")
    public LeaveResponse getLeaveById(@PathVariable Long leaveId) {
        return leaveService.getLeaveById(leaveId);
    }

    @GetMapping("/user/{userId}")
    public List<LeaveResponse> getLeavesByUserId(@PathVariable Long userId) {
        return leaveService.getLeavesByUserId(userId);
    }

    @GetMapping("/types")
    public List<LeaveTypeResponse> getLeaveTypes() {
        return leaveService.getLeaveTypes();
    }
}
