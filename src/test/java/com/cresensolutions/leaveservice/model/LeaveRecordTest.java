package com.cresensolutions.leaveservice.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveRecordTest {

    @Test
    void constructor_setsFieldsCorrectly() throws Exception {
        UserProfile user = buildUser(1L, "john@example.com");
        LeaveType leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);

        LeaveRecord record = new LeaveRecord(user, leaveType, "Vacation", "comment", "trail", true);

        assertThat(record.getReason()).isEqualTo("Vacation");
        assertThat(record.getComments()).isEqualTo("comment");
        assertThat(record.getTrail()).isEqualTo("trail");
        assertThat(record.isEditable()).isTrue();
        assertThat(record.getStatus()).isEqualTo("PENDING");
        assertThat(record.getEmailId()).isEqualTo("john@example.com");
        assertThat(record.getUser()).isEqualTo(user);
    }

    @Test
    void updateStatus_updatesFields() throws Exception {
        LeaveRecord record = buildLeaveRecord();

        record.updateStatus("APPROVED", "manager1", null);

        assertThat(record.getStatus()).isEqualTo("APPROVED");
        assertThat(record.getApprovedBy()).isEqualTo("manager1");
        assertThat(record.getRejectionReason()).isNull();
    }

    @Test
    void updateStatus_rejected_setsRejectionReason() throws Exception {
        LeaveRecord record = buildLeaveRecord();

        record.updateStatus("REJECTED", "manager1", "Not enough notice");

        assertThat(record.getStatus()).isEqualTo("REJECTED");
        assertThat(record.getRejectionReason()).isEqualTo("Not enough notice");
    }

    @Test
    void updateDetails_updatesLeaveTypeAndReason() throws Exception {
        LeaveRecord record = buildLeaveRecord();
        LeaveType newType = new LeaveType("Sick Leave", "SICK_LEAVE", null, 10, null);

        record.updateDetails(newType, "Sick", "new comment", "new trail");

        assertThat(record.getReason()).isEqualTo("Sick");
        assertThat(record.getComments()).isEqualTo("new comment");
        assertThat(record.getLeaveType()).isEqualTo("SICK_LEAVE");
    }

    @Test
    void getLeaveType_withLeaveTypeReference_returnsDisplayName() throws Exception {
        LeaveRecord record = buildLeaveRecord();
        assertThat(record.getLeaveType()).isEqualTo("ANNUAL_LEAVE");
    }

    @Test
    void getLeaveTypeId_withLeaveTypeReference_returnsId() throws Exception {
        LeaveType leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        setField(leaveType, "id", 1);
        UserProfile user = buildUser(1L, "john@example.com");
        LeaveRecord record = new LeaveRecord(user, leaveType, "Vacation", null, null, true);

        assertThat(record.getLeaveTypeId()).isEqualTo(1);
    }

    @Test
    void getUserId_withUser_returnsUserId() throws Exception {
        UserProfile user = buildUser(1L, "john@example.com");
        LeaveRecord record = new LeaveRecord(user, null, "reason", null, null, true);

        assertThat(record.getUserId()).isEqualTo(1L);
    }

    @Test
    void getUserId_withNullUser_returnsNull() {
        LeaveRecord record = new LeaveRecord(null, null, "reason", null, null, true);
        assertThat(record.getUserId()).isNull();
    }

    @Test
    void addAndClearLeaveDates() throws Exception {
        LeaveRecord record = buildLeaveRecord();
        LeaveDate ld = new LeaveDate(record, LocalDate.now(), "FULL");

        record.addLeaveDate(ld);
        assertThat(record.getLeaveDates()).hasSize(1);

        record.clearLeaveDates();
        assertThat(record.getLeaveDates()).isEmpty();
    }

    private LeaveRecord buildLeaveRecord() throws Exception {
        UserProfile user = buildUser(1L, "john@example.com");
        LeaveType leaveType = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        setField(leaveType, "id", 1);
        return new LeaveRecord(user, leaveType, "Vacation", null, null, true);
    }

    private UserProfile buildUser(Long id, String email) throws Exception {
        UserProfile user = new UserProfile();
        setField(user, "id", id);
        setField(user, "emailId", email);
        setField(user, "active", true);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
