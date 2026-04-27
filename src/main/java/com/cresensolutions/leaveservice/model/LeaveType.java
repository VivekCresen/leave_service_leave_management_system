package com.cresensolutions.leaveservice.model;

import com.cresensolutions.leaveservice.config.DbSchemas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        schema = DbSchemas.LEAVE,
        name = "leave_types",
        indexes = {
                @Index(name = "idx_leave_types_name", columnList = "leave_name"),
                @Index(name = "idx_leave_types_unique_name", columnList = "leave_unique_name")
        }
)
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "leave_name")
    private String leaveName;

    @Column(name = "leave_unique_name")
    private String leaveUniqueName;

    @Column(name = "description")
    private String description;

    @Column(name = "max_days")
    private Integer maxDays;

    @Column(name = "gender_restriction")
    private String genderRestriction; 

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

    public LeaveType(String leaveName, String leaveUniqueName, String description, Integer maxDays) {
        this.leaveName = leaveName;
        this.leaveUniqueName = leaveUniqueName;
        this.description = description;
        this.maxDays = maxDays;
    }

    public LeaveType(String leaveName, String leaveUniqueName, String description, Integer maxDays, String genderRestriction) {
        this.leaveName = leaveName;
        this.leaveUniqueName = leaveUniqueName;
        this.description = description;
        this.maxDays = maxDays;
        this.genderRestriction = genderRestriction;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    public String getGenderRestriction() {
        return genderRestriction;
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

    public void updateDetails(String leaveName, String leaveUniqueName, String description, Integer maxDays, String genderRestriction) {
        this.leaveName = leaveName;
        this.leaveUniqueName = leaveUniqueName;
        this.description = description;
        this.maxDays = maxDays;
        this.genderRestriction = genderRestriction;
    }

    void addLeaveRecord(LeaveRecord leaveRecord) {
        leaveRecords.add(leaveRecord);
    }

    void removeLeaveRecord(LeaveRecord leaveRecord) {
        leaveRecords.remove(leaveRecord);
    }
}
