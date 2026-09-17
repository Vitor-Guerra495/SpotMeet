package com.spotmeet.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Organization (company) registered in SpotMeet.
 *
 * Business rules:
 *   - The access key is created by the owner and must start with '#' (e.g. "#Minerva").
 *   - Lookup by access key is strictly case-sensitive.
 *   - 'approved' is false by default; a SysAdmin must approve before use.
 *   - The owner is the first ORG_OWNER and is also registered in OrganizationMember.
 */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * Unique access key of the organization.
     * Format: '#' followed by 2-50 alphanumeric characters, '_' or '-'.
     * Stored exactly as provided; PostgreSQL comparison is case-sensitive.
     */
    @Column(name = "access_key", unique = true, nullable = false, length = 100)
    private String accessKey;

    /**
     * Owner of the organization (ORG_OWNER).
     * Also registered in OrganizationMember with role = ORG_OWNER.
     * Kept here for fast direct access without a join.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Optional CNPJ. */
    @Column(unique = true)
    private String cnpj;

    /**
     * SysAdmin approval. False by default; no member can join while approved=false.
     */
    @Column(nullable = false)
    private boolean approved = false;

    /** Lifecycle status: PENDING | APPROVED | BLOCKED | REJECTED */
    @Column(name = "status")
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Organization() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
