package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Checks a recovery code on its own, before the new password is typed
 * (step 2 of the three-step account recovery flow).
 */
public class VerifyRecoveryCodeDTO {

    @NotBlank(message = "E-mail é obrigatório.")
    private String email;

    @NotBlank(message = "O token ou código de recuperação é obrigatório.")
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
