package com.spotmeet.backend.dto;

import java.time.LocalDateTime;

/**
 * DTO for organization listing and management by the SysAdmin.
 *
 * LGPD compliance:
 *   - Owner e-mail is masked (e.g. j***o@domain.com).
 *   - CNPJ is masked (e.g. XX.XXX.XXX/0001-XX).
 */
public class AdminOrganizationDTO {

    private Long id;
    private String name;
    private String accessKey;
    private Long ownerId;
    private String ownerName;
    private String ownerEmailMasked;
    private String cnpjMasked;
    private boolean approved;
    private String status;
    private LocalDateTime createdAt;
    private int totalMembers;
    private int totalCommittees;

    public AdminOrganizationDTO() {}

    public AdminOrganizationDTO(Long id, String name, String accessKey, Long ownerId, String ownerName,
                                String ownerEmailMasked, String cnpjMasked, boolean approved,
                                String status, LocalDateTime createdAt, int totalMembers, int totalCommittees) {
        this.id = id;
        this.name = name;
        this.accessKey = accessKey;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.ownerEmailMasked = ownerEmailMasked;
        this.cnpjMasked = cnpjMasked;
        this.approved = approved;
        this.status = status;
        this.createdAt = createdAt;
        this.totalMembers = totalMembers;
        this.totalCommittees = totalCommittees;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerEmailMasked() { return ownerEmailMasked; }
    public void setOwnerEmailMasked(String ownerEmailMasked) { this.ownerEmailMasked = ownerEmailMasked; }

    public String getCnpjMasked() { return cnpjMasked; }
    public void setCnpjMasked(String cnpjMasked) { this.cnpjMasked = cnpjMasked; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getTotalMembers() { return totalMembers; }
    public void setTotalMembers(int totalMembers) { this.totalMembers = totalMembers; }

    public int getTotalCommittees() { return totalCommittees; }
    public void setTotalCommittees(int totalCommittees) { this.totalCommittees = totalCommittees; }
}
