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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;


@Entity
@Table(
        name = "leave_notify_users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_leave_notify_user",
                        columnNames = {"leave_application_id", "user_id"}
                )
        }
)
public class LeaveNotifyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notify_leave_app"))
    private LeaveRecord leave;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notify_user"))
    private UserProfile user;

    protected LeaveNotifyUser() {}

    public LeaveNotifyUser(LeaveRecord leave, UserProfile user) {
        this.leave = leave;
        this.user = user;
    }

    public Long getId() { return id; }

    public LeaveRecord getLeave() { return leave; }

    public UserProfile getUser() { return user; }

    public Long getUserId() { return user == null ? null : user.getId(); }

    public String getUserEmail() { return user == null ? null : user.getEmailId(); }

    public String getUserFullName() { return user == null ? null : user.getFullName(); }
}
