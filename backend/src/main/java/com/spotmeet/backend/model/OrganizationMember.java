package com.spotmeet.backend.model;

import jakarta.persistence.*;

/**
 * Relationship between User and Organization with hierarchical roles.
 *
 * Role hierarchy (highest to lowest privilege):
 *   ORG_OWNER      -> Absolute owner. Can promote/demote any member.
 *   ORG_VICE_OWNER -> Vice owner. Manages members and committees, can promote to subowner.
 *   ORG_SUBOWNER   -> Subowner. Manages committees and committee members.
 *   MEMBER         -> Regular member. Read-only access to the organization structure.
 */
@Entity
@Table(
    name = "organization_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "organization_id"})
)
public class OrganizationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private OrganizationRole role = OrganizationRole.MEMBER;

    public OrganizationMember() {}

    public enum OrganizationRole {
        ORG_OWNER,
        ORG_VICE_OWNER,
        ORG_SUBOWNER,
        MEMBER
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public OrganizationRole getRole() { return role; }
    public void setRole(OrganizationRole role) { this.role = role; }
}
