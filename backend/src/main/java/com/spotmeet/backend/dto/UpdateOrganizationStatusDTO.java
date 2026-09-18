package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO used by the SysAdmin to approve or block an organization.
 */
public class UpdateOrganizationStatusDTO {

    @NotNull(message = "O campo aprovada é obrigatório (true para aprovar, false para bloquear).")
    private Boolean approved;

    private String justification;

    public UpdateOrganizationStatusDTO() {}

    public UpdateOrganizationStatusDTO(Boolean approved, String justification) {
        this.approved = approved;
        this.justification = justification;
    }

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
}
