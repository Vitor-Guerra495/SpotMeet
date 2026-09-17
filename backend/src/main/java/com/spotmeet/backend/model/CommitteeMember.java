package com.spotmeet.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Relationship between User and Committee.
 *
 * Membership status:
 *   PENDING_USER_ACCEPTANCE  -> Leader invited the user, waiting for the user to accept.
 *   PENDING_LEADER_APPROVAL  -> User requested to join, waiting for leader approval.
 *   ACTIVE                   -> Active membership.
 *   DECLINED                 -> Declined by the user or rejected by the leader.
 *
 * Committee roles: COMMITTEE_ADMIN, MEMBER.
 * LGPD audit fields: invitedBy, requestedAt, respondedAt.
 */
@Entity
@Table(
    name = "committee_members",
    uniqueConstraints = @UniqueConstraint(name = "uq_committee_member", columnNames = {"user_id", "committee_id"})
)
public class CommitteeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", nullable = false)
    private Committee committee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CommitteeMembershipStatus status = CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CommitteeRole role = CommitteeRole.MEMBER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private User invitedBy;

    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public CommitteeMember() {}

    public CommitteeMember(User user, Committee committee, CommitteeMembershipStatus status, CommitteeRole role, User invitedBy) {
        this.user = user;
        this.committee = committee;
        this.status = status;
        this.role = role;
        this.invitedBy = invitedBy;
    }

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
    }

    // Enums

    public enum CommitteeMembershipStatus {
        PENDING_USER_ACCEPTANCE,
        PENDING_LEADER_APPROVAL,
        ACTIVE,
        DECLINED
    }

    public enum CommitteeRole {
        COMMITTEE_ADMIN,
        MEMBER
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Committee getCommittee() { return committee; }
    public void setCommittee(Committee committee) { this.committee = committee; }

    public CommitteeMembershipStatus getStatus() { return status; }
    public void setStatus(CommitteeMembershipStatus status) { this.status = status; }

    public CommitteeRole getRole() { return role; }
    public void setRole(CommitteeRole role) { this.role = role; }

    public User getInvitedBy() { return invitedBy; }
    public void setInvitedBy(User invitedBy) { this.invitedBy = invitedBy; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
