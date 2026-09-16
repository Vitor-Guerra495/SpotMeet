package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for step 2 of the secure e-mail change flow (LGPD).
 * Validates the code sent to the new e-mail and completes the change.
 */
public class CompleteEmailChangeDTO {

    @NotBlank(message = "O código enviado ao novo e-mail é obrigatório.")
    @Pattern(regexp = "^\\d{6}$", message = "O código de verificação deve conter 6 dígitos numéricos.")
    private String newEmailCode;

    public CompleteEmailChangeDTO() {}

    public CompleteEmailChangeDTO(String newEmailCode) {
        this.newEmailCode = newEmailCode;
    }

    public String getNewEmailCode() { return newEmailCode; }
    public void setNewEmailCode(String newEmailCode) { this.newEmailCode = newEmailCode; }
}
