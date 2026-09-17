package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.Organization;
import java.time.LocalDateTime;

/**
 * Organization response DTO.
 *
 * LGPD / security: never exposes 'cnpj' (sensitive data).
 */
public class OrganizationResponseDTO {

    private Long id;
    private String name;
    private String accessKey;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
    private String myRole;
    private boolean approved;
    private String status;

    public OrganizationResponseDTO() {}

    public static OrganizationResponseDTO fromEntity(Organization org) {
        OrganizationResponseDTO dto = new OrganizationResponseDTO();
        dto.id = org.getId();
        dto.name = org.getName();
        dto.accessKey = org.getAccessKey();
        dto.ownerId = org.getOwner().getId();
        dto.ownerName = org.getOwner().getName();
        dto.createdAt = org.getCreatedAt();
        dto.approved = org.isApproved();
        dto.status = org.getStatus() != null ? org.getStatus() : (org.isApproved() ? "APPROVED" : "PENDING");
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAccessKey() { return accessKey; }
    public Long getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getMyRole() { return myRole; }
    public void setMyRole(String myRole) { this.myRole = myRole; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * Member representation for listing by the owner.
     * Exposes only name, e-mail and role.
     */
    public static class MemberDTO {
        private final Long userId;
        private final String name;
        private final String email;
        private final String role;

        public MemberDTO(Long userId, String name, String email, String role) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.role = role;
        }

        public Long getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
    }
}
