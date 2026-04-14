package com.cresensolutions.leaveservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveTypeTest {

    @Test
    void constructor_setsAllFields() {
        LeaveType lt = new LeaveType("Annual Leave", "ANNUAL_LEAVE", "Annual leave desc", 20, "MALE");

        assertThat(lt.getLeaveName()).isEqualTo("Annual Leave");
        assertThat(lt.getLeaveUniqueName()).isEqualTo("ANNUAL_LEAVE");
        assertThat(lt.getDescription()).isEqualTo("Annual leave desc");
        assertThat(lt.getMaxDays()).isEqualTo(20);
        assertThat(lt.getGenderRestriction()).isEqualTo("MALE");
    }

    @Test
    void getDisplayName_withUniqueName_returnsUniqueName() {
        LeaveType lt = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        assertThat(lt.getDisplayName()).isEqualTo("ANNUAL_LEAVE");
    }

    @Test
    void getDisplayName_withBlankUniqueName_returnsLeaveName() {
        LeaveType lt = new LeaveType("Annual Leave", "  ", null, 20, null);
        assertThat(lt.getDisplayName()).isEqualTo("Annual Leave");
    }

    @Test
    void getDisplayName_withNullUniqueName_returnsLeaveName() {
        LeaveType lt = new LeaveType("Annual Leave", null, null, 20, null);
        assertThat(lt.getDisplayName()).isEqualTo("Annual Leave");
    }

    @Test
    void getDisplayName_withNullBoth_returnsEmpty() {
        LeaveType lt = new LeaveType(null, null, null, 20, null);
        assertThat(lt.getDisplayName()).isEmpty();
    }

    @Test
    void updateDetails_updatesAllFields() {
        LeaveType lt = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        lt.updateDetails("Sick Leave", "SICK_LEAVE", "For illness", 10, "FEMALE");

        assertThat(lt.getLeaveName()).isEqualTo("Sick Leave");
        assertThat(lt.getLeaveUniqueName()).isEqualTo("SICK_LEAVE");
        assertThat(lt.getDescription()).isEqualTo("For illness");
        assertThat(lt.getMaxDays()).isEqualTo(10);
        assertThat(lt.getGenderRestriction()).isEqualTo("FEMALE");
    }

    @Test
    void addAndRemoveLeaveRecord() {
        LeaveType lt = new LeaveType("Annual Leave", "ANNUAL_LEAVE", null, 20, null);
        LeaveRecord record = new LeaveRecord(null, lt, "reason", null, null, true);

        lt.addLeaveRecord(record);
        assertThat(lt.getLeaveRecords()).contains(record);

        lt.removeLeaveRecord(record);
        assertThat(lt.getLeaveRecords()).doesNotContain(record);
    }

    @Test
    void idConstructor_setsIdNameAndUniqueName() {
        LeaveType lt = new LeaveType(5, "Sick Leave", "SICK_LEAVE");
        assertThat(lt.getLeaveName()).isEqualTo("Sick Leave");
        assertThat(lt.getLeaveUniqueName()).isEqualTo("SICK_LEAVE");
    }
}
