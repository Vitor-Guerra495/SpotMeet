package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Registration DTO. Prevents sensitive fields (role, organization)
 * from being injected via JSON (mass assignment protection).
 */
public class RegisterRequestDTO {

    @NotBlank(message = "Nome é obrigatório.")
    private String name;

    @NotBlank(message = "E-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    /**
     * Strong password rule (OWASP): at least 6 characters, one lowercase,
     * one uppercase and one digit. The same regex is applied in the app.
     */
    @NotBlank(message = "Senha é obrigatória.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$",
        message = "Senha deve ter no mínimo 6 caracteres, com pelo menos uma letra maiúscula, uma minúscula e um número."
    )
    private String password;

    public RegisterRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
