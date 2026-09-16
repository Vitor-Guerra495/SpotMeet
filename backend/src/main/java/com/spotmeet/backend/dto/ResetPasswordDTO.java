package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ResetPasswordDTO {

    @NotBlank(message = "E-mail é obrigatório.")
    private String email;

    @NotBlank(message = "O token ou código de recuperação é obrigatório.")
    private String token;

    @NotBlank(message = "A nova senha é obrigatória.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$",
        message = "A nova senha deve ter no mínimo 6 caracteres, com pelo menos uma letra maiúscula, uma minúscula e um número."
    )
    private String newPassword;

    public ResetPasswordDTO() {}

    public ResetPasswordDTO(String email, String token, String newPassword) {
        this.email = email;
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
