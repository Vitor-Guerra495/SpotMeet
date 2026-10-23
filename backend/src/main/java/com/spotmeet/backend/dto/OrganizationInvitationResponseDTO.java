package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.OrganizationInvitation;
import com.spotmeet.backend.model.OrganizationInvitation.InvitationStatus;
import java.time.Instant;

public class OrganizationInvitationResponseDTO {
    private Long id;
    private Long organizationId;
    private String organizationName;
    private Long userId;
    private Long invitedById;
    private String invitedByName;
    private InvitationStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant respondedAt;

    public static OrganizationInvitationResponseDTO fromEntity(OrganizationInvitation invitation) {
        OrganizationInvitationResponseDTO dto = new OrganizationInvitationResponseDTO();
        dto.id = invitation.getId();
        dto.organizationId = invitation.getOrganization().getId();
        dto.organizationName = invitation.getOrganization().getName();
        dto.userId = invitation.getUser().getId();
        dto.invitedById = invitation.getInvitedBy().getId();
        dto.invitedByName = invitation.getInvitedBy().getName();
        dto.status = invitation.getStatus();
        dto.createdAt = invitation.getCreatedAt();
        dto.expiresAt = invitation.getExpiresAt();
        dto.respondedAt = invitation.getRespondedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getOrganizationName() { return organizationName; }
    public Long getUserId() { return userId; }
    public Long getInvitedById() { return invitedById; }
    public String getInvitedByName() { return invitedByName; }
    public InvitationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
