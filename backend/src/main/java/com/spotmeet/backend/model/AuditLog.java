package com.spotmeet.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit record of critical system actions (LGPD / traceability).
 * Immutable after creation: audit records are never updated or deleted.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User who performed the action. May be null for anonymous actions. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String action;

    /** Optional extra details (e.g. source IP, affected resource). */
    @Column(length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {}

    public AuditLog(User user, String action) {
        this.user = user;
        this.action = action;
        this.createdAt = LocalDateTime.now();
    }

    public AuditLog(User user, String action, String details) {
        this(user, action);
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // Getters (no setter for createdAt to keep records immutable)

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
