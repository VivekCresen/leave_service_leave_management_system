package com.cresensolutions.leaveservice.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveNotifyUserTest {

    @Test
    void constructor_setsLeaveAndUser() throws Exception {
        LeaveRecord leave = new LeaveRecord(null, null, "reason", null, null, true);
        UserProfile user = new UserProfile();
        setField(user, "id", 5L);
        setField(user, "emailId", "user@example.com");
        setField(user, "fullName", "Test User");

        LeaveNotifyUser notifyUser = new LeaveNotifyUser(leave, user);

        assertThat(notifyUser.getLeave()).isEqualTo(leave);
        assertThat(notifyUser.getUser()).isEqualTo(user);
        assertThat(notifyUser.getUserId()).isEqualTo(5L);
        assertThat(notifyUser.getUserEmail()).isEqualTo("user@example.com");
        assertThat(notifyUser.getUserFullName()).isEqualTo("Test User");
    }

    @Test
    void getUserId_withNullUser_returnsNull() {
        LeaveNotifyUser notifyUser = new LeaveNotifyUser(null, null);
        assertThat(notifyUser.getUserId()).isNull();
        assertThat(notifyUser.getUserEmail()).isNull();
        assertThat(notifyUser.getUserFullName()).isNull();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
