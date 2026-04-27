package com.cresensolutions.leaveservice.model;

import com.cresensolutions.leaveservice.config.DbSchemas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = DbSchemas.LEAVE, name = "employee_leave")
public class EmployeeLeave {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserProfile user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email_id")
    private String emailId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leaves", columnDefinition = "jsonb")
    private String leaves;

    @Column(name = "gender")
    private String gender;

    protected EmployeeLeave() {
    }

    public Long getId() {
        return id;
    }

    public UserProfile getUser() {
        return user;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getLeaves() {
        return leaves;
    }

    public String getGender() {
        return gender;
    }
}
