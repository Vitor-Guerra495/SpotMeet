package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationRejectionDTO {
    @NotBlank(message = "Informe o motivo da recusa.")
    @Size(min = 3, max = 500, message = "O motivo deve ter de 3 a 500 caracteres.")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? null : reason.replaceAll(ValidationPatterns.NAME_WHITESPACE, ""); }
}
