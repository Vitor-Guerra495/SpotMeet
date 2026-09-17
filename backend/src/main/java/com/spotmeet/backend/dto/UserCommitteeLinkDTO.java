package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;

/**
 * A committee of the organization together with the membership state of a specific user.
 * Used by the app to render the per-user committee links modal.
 */
public class UserCommitteeLinkDTO {

    private Long committeeId;
    private String committeeName;
    private String description;
    private boolean linked;
    private CommitteeMembershipStatus membershipStatus;
    private CommitteeRole role;
    private Long membershipId;

    public UserCommitteeLinkDTO() {}

    public UserCommitteeLinkDTO(Long committeeId, String committeeName, String description,
                                boolean linked, CommitteeMembershipStatus membershipStatus,
                                CommitteeRole role, Long membershipId) {
        this.committeeId = committeeId;
        this.committeeName = committeeName;
        this.description = description;
        this.linked = linked;
        this.membershipStatus = membershipStatus;
        this.role = role;
        this.membershipId = membershipId;
    }

    public Long getCommitteeId() { return committeeId; }
    public void setCommitteeId(Long committeeId) { this.committeeId = committeeId; }

    public String getCommitteeName() { return committeeName; }
    public void setCommitteeName(String committeeName) { this.committeeName = committeeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isLinked() { return linked; }
    public void setLinked(boolean linked) { this.linked = linked; }

    public CommitteeMembershipStatus getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(CommitteeMembershipStatus membershipStatus) { this.membershipStatus = membershipStatus; }

    public CommitteeRole getRole() { return role; }
    public void setRole(CommitteeRole role) { this.role = role; }

    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
}
