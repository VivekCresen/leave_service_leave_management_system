package com.cresensolutions.leaveservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    private Long id;

    @Column(name = "company_id")
    private String companyId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email_id")
    private String emailId;

    @Column(name = "user_pswd")
    private String userPswd;

    @Column(name = "role")
    private String role;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "gender")
    private String gender;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "create_date")
    private Instant createDate;

    @Column(name = "update_date")
    private Instant updateDate;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "last_login")
    private Instant lastLogin;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<LeaveRecord> leaveRecords = new LinkedHashSet<>();

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private EmployeeLeave employeeLeave;

    public UserProfile() {
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getUserName() {
        return userName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getUserPswd() {
        return userPswd;
    }

    public String getRole() {
        return role;
    }

    public Long getRoleId() {
        return roleId;
    }

    public String getGender() {
        return gender;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreateDate() {
        return createDate;
    }

    public Instant getUpdateDate() {
        return updateDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public Set<LeaveRecord> getLeaveRecords() {
        return Collections.unmodifiableSet(leaveRecords);
    }

    public EmployeeLeave getEmployeeLeave() {
        return employeeLeave;
    }

    void addLeaveRecord(LeaveRecord leaveRecord) {
        leaveRecords.add(leaveRecord);
    }

    void removeLeaveRecord(LeaveRecord leaveRecord) {
        leaveRecords.remove(leaveRecord);
    }
}
