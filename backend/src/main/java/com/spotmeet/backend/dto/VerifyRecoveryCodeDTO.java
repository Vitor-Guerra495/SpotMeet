package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Checks a recovery code on its own, before the new password is typed
 * (step 2 of the three-step account recovery flow).
 */
public class VerifyRecoveryCodeDTO {

    @NotBlank(message = "E-mail é obrigatório.")
    @Email(regexp = ValidationPatterns.EMAIL, message = "Formato de e-mail inválido.")
    private String email;

    @NotBlank(message = "O token ou código de recuperação é obrigatório.")
    @Pattern(regexp = ValidationPatterns.OTP, message = "O código de verificação deve conter 6 dígitos numéricos.")
    private String token;

    public VerifyRecoveryCodeDTO() {}

    public VerifyRecoveryCodeDTO(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
