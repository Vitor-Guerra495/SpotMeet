package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationResubmitDTO {
    @NotBlank(message = "Informe a justificativa do reenvio.")
    @Size(min = 3, max = 500, message = "A justificativa deve ter de 3 a 500 caracteres.")
    private String justification;

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification == null ? null : justification.replaceAll(ValidationPatterns.NAME_WHITESPACE, ""); }
}
