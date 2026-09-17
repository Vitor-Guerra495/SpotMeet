package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.CommitteeMember;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;

import java.time.LocalDateTime;

/**
 * DTO representing a committee membership, invitation or join request.
 */
public class CommitteeMemberResponseDTO {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long committeeId;
    private String committeeName;
    private Long organizationId;
    private CommitteeMembershipStatus status;
    private CommitteeRole role;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
    private String invitedByName;

    public CommitteeMemberResponseDTO() {}

    public static CommitteeMemberResponseDTO fromEntity(CommitteeMember cm) {
        CommitteeMemberResponseDTO dto = new CommitteeMemberResponseDTO();
        dto.id = cm.getId();
        dto.userId = cm.getUser().getId();
        dto.userName = cm.getUser().getName();
        dto.userEmail = cm.getUser().getEmail();
        dto.committeeId = cm.getCommittee().getId();
        dto.committeeName = cm.getCommittee().getName();
        dto.organizationId = cm.getCommittee().getOrganization().getId();
        dto.status = cm.getStatus();
        dto.role = cm.getRole();
        dto.requestedAt = cm.getRequestedAt();
        dto.respondedAt = cm.getRespondedAt();
        if (cm.getInvitedBy() != null) {
            dto.invitedByName = cm.getInvitedBy().getName();
        }
        return dto;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserEmail() { return userEmail; }
    public Long getCommitteeId() { return committeeId; }
    public String getCommitteeName() { return committeeName; }
    public Long getOrganizationId() { return organizationId; }
    public CommitteeMembershipStatus getStatus() { return status; }
    public CommitteeRole getRole() { return role; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public String getInvitedByName() { return invitedByName; }
}
