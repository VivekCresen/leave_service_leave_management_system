package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.LeaveDate;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.LeaveDateRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveReminderSchedulerTest {

    @Mock private LeaveRepository        leaveRepository;
    @Mock private LeaveDateRepository    leaveDateRepository;
    @Mock private UserProfileRepository  userProfileRepository;
    @Mock private LeaveEmailService      leaveEmailService;

    @InjectMocks private LeaveReminderScheduler scheduler;

    private LeaveRecord pendingLeave;
    private UserProfile employee;
    private UserProfile manager;

    @BeforeEach
    void setUp() throws Exception {
        employee = new UserProfile();
        setField(employee, "id",        1L);
        setField(employee, "fullName",  "John Doe");
        setField(employee, "emailId",   "john@example.com");
        setField(employee, "active",    true);
        setField(employee, "createdBy", "manager1");

        manager = new UserProfile();
        setField(manager, "id",       2L);
        setField(manager, "userName", "manager1");
        setField(manager, "emailId",  "manager@example.com");
        setField(manager, "active",   true);

        LeaveType leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        pendingLeave = new LeaveRecord(employee, leaveType, "Vacation", null, null, true);
        setField(pendingLeave, "id",     10L);
        setField(pendingLeave, "status", "PENDING");
    }


    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
