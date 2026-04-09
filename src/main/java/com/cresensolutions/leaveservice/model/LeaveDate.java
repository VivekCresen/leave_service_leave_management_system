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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
        name = "leave_dates",
        indexes = {
                @Index(name = "idx_leave_dates_application_id", columnList = "leave_application_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_leave_date_per_app", columnNames = {"leave_application_id", "leave_date"})
        }
)
public class LeaveDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_leave_dates_app"))
    private LeaveRecord leaveApplication;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(name = "day_type", nullable = false)
    private String dayType = "FULL";

    protected LeaveDate() {}

    public LeaveDate(LeaveRecord leaveApplication, LocalDate leaveDate, String dayType) {
        this.leaveApplication = leaveApplication;
        this.leaveDate = leaveDate;
        this.dayType = dayType != null ? dayType.toUpperCase() : "FULL";
    }

    public Long getId() { return id; }
    public LeaveRecord getLeaveApplication() { return leaveApplication; }
    public LocalDate getLeaveDate() { return leaveDate; }
    public String getDayType() { return dayType; }
}
