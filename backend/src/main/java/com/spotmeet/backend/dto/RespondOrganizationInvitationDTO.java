package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotNull;

public class RespondOrganizationInvitationDTO {
    @NotNull(message = "Informe se deseja aceitar ou recusar o convite.")
    private Boolean accept;

    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
}
