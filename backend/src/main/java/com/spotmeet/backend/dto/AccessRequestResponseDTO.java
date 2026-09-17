package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.AccessRequest;
import com.spotmeet.backend.model.AccessRequest.AccessRequestStatus;

import java.time.LocalDateTime;

/**
 * Access request response DTO.
 *
 * LGPD: exposes only what the owner needs to decide.
 * Never exposes password, tokens or other sensitive fields of the requester.
 */
public class AccessRequestResponseDTO {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long organizationId;
    private String organizationName;
    private String organizationAccessKey;
    private AccessRequestStatus status;
    private LocalDateTime requestedAt;

    public AccessRequestResponseDTO() {}

    /** Convenience factory from the entity. */
    public static AccessRequestResponseDTO fromEntity(AccessRequest r) {
        AccessRequestResponseDTO dto = new AccessRequestResponseDTO();
        dto.id = r.getId();
        dto.userId = r.getUser().getId();
        dto.userName = r.getUser().getName();
        dto.userEmail = r.getUser().getEmail();
        dto.organizationId = r.getOrganization().getId();
        dto.organizationName = r.getOrganization().getName();
        dto.organizationAccessKey = r.getOrganization().getAccessKey();
        dto.status = r.getStatus();
        dto.requestedAt = r.getRequestedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserEmail() { return userEmail; }
    public Long getOrganizationId() { return organizationId; }
    public String getOrganizationName() { return organizationName; }
    public String getOrganizationAccessKey() { return organizationAccessKey; }
    public AccessRequestStatus getStatus() { return status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}
