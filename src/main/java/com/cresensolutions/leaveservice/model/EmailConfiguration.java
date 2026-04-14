package com.cresensolutions.leaveservice.model;

import com.cresensolutions.leaveservice.config.DbSchemas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = DbSchemas.EMAIL, name = "email_configuration")
public class EmailConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "protocol", nullable = false)
    private String protocol = "smtp";

    @Column(name = "auth", nullable = false)
    private boolean auth = true;

    @Column(name = "starttls_enabled", nullable = false)
    private boolean starttlsEnabled = true;

    @Column(name = "ssl_enabled", nullable = false)
    private boolean sslEnabled;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "logo_path")
    private String logoPath;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize = 25;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailConfiguration() {
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

    public Long getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProtocol() {
        return protocol;
    }

    public boolean isAuth() {
        return auth;
    }

    public boolean isStarttlsEnabled() {
        return starttlsEnabled;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public boolean isActive() {
        return active;
    }
}
