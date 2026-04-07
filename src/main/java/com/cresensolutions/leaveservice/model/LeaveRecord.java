package com.cresensolutions.leaveservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(
        name = "\"leave\"",
        indexes = {
                @Index(name = "idx_leave_user_from_date", columnList = "user_id, from_date"),
                @Index(name = "idx_leave_type_reference", columnList = "leave_type_id"),
                @Index(name = "idx_leave_from_date", columnList = "from_date")
        }
)
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

    @Column(name = "status")
    private String status = "PENDING";

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "half_day")
    private Boolean halfDay = false;

    @Column(name = "half_day_session")
    private String halfDaySession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_leave_user"))
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", foreignKey = @ForeignKey(name = "fk_leave_type"))
    private LeaveType leaveTypeReference;

    protected LeaveRecord() {}

    protected LeaveRecord(UserProfile userProfile, LeaveType linkedType, LocalDate now, LocalDate toDate, String trip, String ok, String trail, boolean editable) {
    }

    public LeaveRecord(
            UserProfile user,
            LeaveType leaveTypeReference,
            LocalDate fromDate,
            LocalDate toDate,
            String reason,
            String comments,
            String trail,
            boolean editable,
            boolean halfDay,
            String halfDaySession
    ) {
        assignUser(user);
        assignLeaveType(leaveTypeReference);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.reason = reason;
        this.comments = comments;
        this.trail = trail;
        this.editable = editable;
        this.halfDay = halfDay;
        this.halfDaySession = halfDay ? normalizeSession(halfDaySession) : null;
    }

    private static String normalizeSession(String session) {
        if (session == null) return null;
        String upper = session.trim().toUpperCase();
        return (upper.equals("MORNING") || upper.equals("AFTERNOON")) ? upper : null;
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

    public String getStatus() {
        return status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public boolean isHalfDay() {
        return Boolean.TRUE.equals(halfDay);
    }

    public String getHalfDaySession() {
        return halfDaySession;
    }

    public void updateStatus(String status, String actorUsername, String rejectionReason) {
        this.status = status;
        this.approvedBy = actorUsername;
        this.rejectionReason = rejectionReason;
    }

    public void updateDetails(LeaveType leaveTypeReference, LocalDate fromDate, LocalDate toDate,
                              String reason, String comments, String trail,
                              boolean halfDay, String halfDaySession) {
        assignLeaveType(leaveTypeReference);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.reason = reason;
        this.comments = comments;
        this.trail = trail;
        this.halfDay = halfDay;
        this.halfDaySession = halfDay ? normalizeSession(halfDaySession) : null;
    }

    public UserProfile getUser() {
        return user;
    }

    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    public void assignUser(UserProfile user) {
        if (this.user != null && this.user != user) {
            this.user.removeLeaveRecord(this);
        }

        this.user = user;
        if (user != null) {
            user.addLeaveRecord(this);
            this.emailId = user.getEmailId();
        }
    }

    public void assignLeaveType(LeaveType leaveTypeReference) {
        if (this.leaveTypeReference != null && this.leaveTypeReference != leaveTypeReference) {
            this.leaveTypeReference.removeLeaveRecord(this);
        }

        this.leaveTypeReference = leaveTypeReference;
        if (leaveTypeReference != null) {
            leaveTypeReference.addLeaveRecord(this);
            this.leaveType = leaveTypeReference.getDisplayName();
        }
    }
}
