package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public class OrganizationInvitationRequestDTO {
    @NotBlank(message = "E-mail é obrigatório.")
    @Email(regexp = ValidationPatterns.EMAIL, message = "Formato de e-mail inválido.")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
