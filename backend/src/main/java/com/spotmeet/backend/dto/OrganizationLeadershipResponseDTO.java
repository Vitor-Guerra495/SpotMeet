package com.spotmeet.backend.dto;

public class OrganizationLeadershipResponseDTO {
    private final Long organizationId;
    private final Long previousOwnerId;
    private final Long ownerId;
    private final String previousOwnerRole;
    private final String ownerRole;

    public OrganizationLeadershipResponseDTO(Long organizationId, Long previousOwnerId, Long ownerId,
            String previousOwnerRole, String ownerRole) {
        this.organizationId = organizationId;
        this.previousOwnerId = previousOwnerId;
        this.ownerId = ownerId;
        this.previousOwnerRole = previousOwnerRole;
        this.ownerRole = ownerRole;
    }

    public Long getOrganizationId() { return organizationId; }
    public Long getPreviousOwnerId() { return previousOwnerId; }
    public Long getOwnerId() { return ownerId; }
    public String getPreviousOwnerRole() { return previousOwnerRole; }
    public String getOwnerRole() { return ownerRole; }
}
