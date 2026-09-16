package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for step 1 of the secure e-mail change flow (LGPD).
 * Validates the code sent to the current e-mail and receives the new address.
 */
public class ConfirmEmailChangeDTO {

    @NotBlank(message = "O código enviado ao e-mail atual é obrigatório.")
    @Pattern(regexp = "^\\d{6}$", message = "O código de verificação deve conter 6 dígitos numéricos.")
    private String currentCode;

    @NotBlank(message = "O novo e-mail é obrigatório.")
    @Email(message = "Formato do novo e-mail inválido.")
    private String newEmail;

    public ConfirmEmailChangeDTO() {}

    public ConfirmEmailChangeDTO(String currentCode, String newEmail) {
        this.currentCode = currentCode;
        this.newEmail = newEmail;
    }

    public String getCurrentCode() { return currentCode; }
    public void setCurrentCode(String currentCode) { this.currentCode = currentCode; }

    public String getNewEmail() { return newEmail; }
    public void setNewEmail(String newEmail) { this.newEmail = newEmail; }
}
