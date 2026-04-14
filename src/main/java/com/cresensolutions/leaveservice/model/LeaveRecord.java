package com.cresensolutions.leaveservice.model;

import com.cresensolutions.leaveservice.config.DbSchemas;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        schema = DbSchemas.LEAVE,
        name = "leave_application",
        indexes = {
                @Index(name = "idx_leave_application_user_id", columnList = "user_id"),
                @Index(name = "idx_leave_application_leave_type_id", columnList = "leave_type_id")
        }
)
public class LeaveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_type")
    private String leaveType;

    @Column(name = "email_id")
    private String emailId;

    @Column(name = "reason")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trail", columnDefinition = "jsonb")
    private String trail = "[]";

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

   @Column(name = "reminder_sent_flags")
    private String reminderSentFlags;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_leave_app_user"))
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", foreignKey = @ForeignKey(name = "fk_leave_app_type"))
    private LeaveType leaveTypeReference;

    @OneToMany(mappedBy = "leaveApplication", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LeaveDate> leaveDates = new ArrayList<>();

    protected LeaveRecord() {}

    public LeaveRecord(
            UserProfile user,
            LeaveType leaveTypeReference,
            String reason,
            String comments,
            String trail,
            boolean editable
    ) {
        assignUser(user);
        assignLeaveType(leaveTypeReference);
        this.reason = reason;
        this.comments = comments;
        this.trail = trail;
        this.editable = editable;
    }

    @PrePersist
    void onCreate() {
        LocalDate today = LocalDate.now();
        if (createdAt == null) createdAt = today;
        updatedAt = today;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDate.now();
    }

    public Long getId() { return id; }

    public String getLeaveType() {
        if (leaveTypeReference != null) return leaveTypeReference.getDisplayName();
        return leaveType;
    }

    public Integer getLeaveTypeId() {
        return leaveTypeReference == null ? null : leaveTypeReference.getId();
    }

    public String getEmailId() { return emailId; }
    public String getReason() { return reason; }
    public String getTrail() { return trail; }
    public LocalDate getCreatedAt() { return createdAt; }
    public LocalDate getUpdatedAt() { return updatedAt; }
    public String getComments() { return comments; }
    public boolean isEditable() { return editable; }
    public String getStatus() { return status; }
    public String getApprovedBy() { return approvedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public String getReminderSentFlags() { return reminderSentFlags; }
    public UserProfile getUser() { return user; }

    public boolean isReminderSent(int daysBefore) {
        if (reminderSentFlags == null || reminderSentFlags.isBlank()) return false;
        for (String flag : reminderSentFlags.split(",")) {
            if (flag.trim().equals(String.valueOf(daysBefore))) return true;
        }
        return false;
    }

    public void markReminderSent(int daysBefore) {
        String flag = String.valueOf(daysBefore);
        if (reminderSentFlags == null || reminderSentFlags.isBlank()) {
            reminderSentFlags = flag;
        } else if (!isReminderSent(daysBefore)) {
            reminderSentFlags = reminderSentFlags + "," + flag;
        }
    }
    public Long getUserId() { return user == null ? null : user.getId(); }

    public List<LeaveDate> getLeaveDates() {
        return Collections.unmodifiableList(leaveDates);
    }

    public void addLeaveDate(LeaveDate leaveDate) {
        leaveDates.add(leaveDate);
    }

    public void clearLeaveDates() {
        leaveDates.clear();
    }

    /**
     * Appends a Flowable audit event to the trail JSONB array.
     *
     * @param event             e.g. "SUBMITTED", "PROCESS_STARTED", "APPROVER_RESOLVED",
     *                          "REMINDER_SENT", "APPROVED", "REJECTED"
     * @param actor             username or "system"
     * @param processInstanceId Flowable process instance ID (may be null)
     * @param taskId            Flowable task ID (may be null)
     * @param note              optional free-text detail
     */
    public void appendTrailEntry(String event, String actor,
                                 String processInstanceId, String taskId, String note) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String safeActor = actor != null ? actor.replace("\"", "'") : "system";
        String safeNote  = note  != null ? note.replace("\"", "'")  : "";
        String safePid   = processInstanceId != null ? processInstanceId : "";
        String safeTid   = taskId != null ? taskId : "";

        String entry = String.format(
            "{\"event\":\"%s\",\"actor\":\"%s\",\"timestamp\":\"%s\"," +
            "\"processInstanceId\":\"%s\",\"taskId\":\"%s\",\"note\":\"%s\"}",
            event, safeActor, timestamp, safePid, safeTid, safeNote
        );

        if (this.trail == null || this.trail.isBlank() || this.trail.equals("null")) {
            this.trail = "[" + entry + "]";
        } else {
            // Append to existing array: remove trailing ']', add entry, close
            String trimmed = this.trail.trim();
            if (trimmed.equals("[]")) {
                this.trail = "[" + entry + "]";
            } else {
                this.trail = trimmed.substring(0, trimmed.length() - 1) + "," + entry + "]";
            }
        }
    }

    public void updateStatus(String status, String actorUsername, String rejectionReason) {
        this.status = status;
        this.approvedBy = actorUsername;
        this.rejectionReason = rejectionReason;
    }

    public void updateDetails(LeaveType leaveTypeReference, String reason, String comments, String trail) {
        assignLeaveType(leaveTypeReference);
        this.reason = reason;
        this.comments = comments;
        this.trail = trail;
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
