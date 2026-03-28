package com.cresensolutions.leaveservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "\"leave\"")
public class LeaveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_type")
    private String leaveType;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "email_id")
    private String emailId;

    @Column(name = "reason")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trail")
    private String trail;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @Column(name = "updated_at")
    private LocalDate updatedAt;

    @Column(name = "comments")
    private String comments;

    @Column(name = "editable")
    private boolean editable = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_leave_user"))
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", foreignKey = @ForeignKey(name = "fk_leave_type"))
    private LeaveType leaveTypeReference;

    protected LeaveRecord() {
    }

    public LeaveRecord(
            UserProfile user,
            LeaveType leaveTypeReference,
            LocalDate fromDate,
            LocalDate toDate,
            String reason,
            String comments,
            String trail,
            boolean editable
    ) {
        assignUser(user);
        assignLeaveType(leaveTypeReference);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.reason = reason;
        this.comments = comments;
        this.trail = trail;
        this.editable = editable;
    }

    @PrePersist
    void onCreate() {
        LocalDate today = LocalDate.now();
        if (createdAt == null) {
            createdAt = today;
        }
        updatedAt = today;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public String getLeaveType() {
        if (leaveTypeReference != null) {
            return leaveTypeReference.getDisplayName();
        }
        return leaveType;
    }

    public Integer getLeaveTypeId() {
        return leaveTypeReference == null ? null : leaveTypeReference.getId();
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getReason() {
        return reason;
    }

    public String getTrail() {
        return trail;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public String getComments() {
        return comments;
    }

    public boolean isEditable() {
        return editable;
    }

    public UserProfile getUser() {
        return user;
    }

    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    public void assignUser(UserProfile user) {
        this.user = user;
        if (user != null) {
            this.emailId = user.getEmailId();
        }
    }

    public void assignLeaveType(LeaveType leaveTypeReference) {
        this.leaveTypeReference = leaveTypeReference;
        if (leaveTypeReference != null) {
            this.leaveType = leaveTypeReference.getDisplayName();
        }
    }
}
