package com.cresensolutions.leaveservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    private Integer id;

    @Column(name = "leave_name")
    private String leaveName;

    @Column(name = "leave_unique_name")
    private String leaveUniqueName;

    @Column(name = "description")
    private String description;

    @Column(name = "max_days")
    private Integer maxDays;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "leaveTypeReference", fetch = FetchType.LAZY)
    private Set<LeaveRecord> leaveRecords = new LinkedHashSet<>();

    protected LeaveType() {
    }

    public LeaveType(Integer id, String leaveName, String leaveUniqueName) {
        this.id = id;
        this.leaveName = leaveName;
        this.leaveUniqueName = leaveUniqueName;
    }

    public Integer getId() {
        return id;
    }

    public String getLeaveName() {
        return leaveName;
    }

    public String getLeaveUniqueName() {
        return leaveUniqueName;
    }

    public String getDescription() {
        return description;
    }

    public Integer getMaxDays() {
        return maxDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getDisplayName() {
        if (leaveUniqueName != null && !leaveUniqueName.isBlank()) {
            return leaveUniqueName;
        }
        return leaveName == null ? "" : leaveName;
    }

    public Set<LeaveRecord> getLeaveRecords() {
        return Collections.unmodifiableSet(leaveRecords);
    }

    void addLeaveRecord(LeaveRecord leaveRecord) {
        leaveRecords.add(leaveRecord);
    }

    void removeLeaveRecord(LeaveRecord leaveRecord) {
        leaveRecords.remove(leaveRecord);
    }
}
