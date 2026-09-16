package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordDTO {

    @NotBlank(message = "E-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    public ForgotPasswordDTO() {}

    public ForgotPasswordDTO(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
