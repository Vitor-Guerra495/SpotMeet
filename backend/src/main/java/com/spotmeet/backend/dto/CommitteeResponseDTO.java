package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.Committee;
import java.time.LocalDateTime;

/**
 * Committee response DTO, including member metrics.
 */
public class CommitteeResponseDTO {

    private Long id;
    private String name;
    private String description;
    private Long organizationId;
    private String organizationName;
    private long totalActiveMembers;
    private long totalPending;
    private LocalDateTime createdAt;

    public CommitteeResponseDTO() {}

    public static CommitteeResponseDTO fromEntity(Committee c, long totalActive, long totalPending) {
        CommitteeResponseDTO dto = new CommitteeResponseDTO();
        dto.id = c.getId();
        dto.name = c.getName();
        dto.description = c.getDescription();
        dto.organizationId = c.getOrganization().getId();
        dto.organizationName = c.getOrganization().getName();
        dto.totalActiveMembers = totalActive;
        dto.totalPending = totalPending;
        dto.createdAt = c.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getOrganizationId() { return organizationId; }
    public String getOrganizationName() { return organizationName; }
    public long getTotalActiveMembers() { return totalActiveMembers; }
    public long getTotalPending() { return totalPending; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
