package com.cresensolutions.leaveservice.model;

import com.cresensolutions.leaveservice.config.DbSchemas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(schema = DbSchemas.USER, name = "chat_history")
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserProfile user;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "email_id", nullable = false)
    private String emailId;

    @Column(name = "total_sessions", nullable = false)
    private int totalSessions = 0;

    @Column(name = "total_qa_pairs", nullable = false)
    private int totalQaPairs = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversations", columnDefinition = "jsonb", nullable = false)
    private String conversations = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ChatHistory() {}

    public ChatHistory(UserProfile user) {
        this.user = user;
        this.userName = user.getUserName();
        this.emailId = user.getEmailId();
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void setConversations(String conversations) {
        this.conversations = conversations;
    }

    public void incrementSessions() {
        this.totalSessions++;
    }

    public void incrementQaPairs() {
        this.totalQaPairs++;
    }
}
