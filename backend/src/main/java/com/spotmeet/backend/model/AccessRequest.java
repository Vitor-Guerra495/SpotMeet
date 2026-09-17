package com.spotmeet.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Request from a user to join an organization.
 *
 * Status lifecycle:
 *   PENDING -> APPROVED (owner approves)
 *   PENDING -> REJECTED (owner rejects)
 *
 * LGPD: requestedAt records the exact moment of the request for auditing.
 */
@Entity
@Table(
    name = "access_requests",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "organization_id"})
)
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** Typed status. Every request starts as PENDING. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    /** Moment of creation or reopening of the request. */
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    public AccessRequest() {}

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
    }

    // Status enum

    public enum AccessRequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public AccessRequestStatus getStatus() { return status; }
    public void setStatus(AccessRequestStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
