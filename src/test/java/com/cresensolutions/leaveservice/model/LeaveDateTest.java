package com.cresensolutions.leaveservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveDateTest {

    @Test
    void constructor_withDayType_setsUpperCase() {
        LeaveRecord leave = new LeaveRecord(null, null, "reason", null, null, true);
        LeaveDate ld = new LeaveDate(leave, LocalDate.of(2026, 4, 10), "full");

        assertThat(ld.getDayType()).isEqualTo("FULL");
        assertThat(ld.getLeaveDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(ld.getLeaveApplication()).isEqualTo(leave);
    }

    @Test
    void constructor_withNullDayType_defaultsFull() {
        LeaveRecord leave = new LeaveRecord(null, null, "reason", null, null, true);
        LeaveDate ld = new LeaveDate(leave, LocalDate.now(), null);

        assertThat(ld.getDayType()).isEqualTo("FULL");
    }

    @Test
    void constructor_withHalfDayType_setsCorrectly() {
        LeaveRecord leave = new LeaveRecord(null, null, "reason", null, null, true);
        LeaveDate ld = new LeaveDate(leave, LocalDate.now(), "first_half");

        assertThat(ld.getDayType()).isEqualTo("FIRST_HALF");
    }
}
