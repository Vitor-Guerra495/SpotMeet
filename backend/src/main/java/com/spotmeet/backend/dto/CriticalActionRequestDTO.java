package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for critical system actions (restart / reset).
 */
public class CriticalActionRequestDTO {

    @NotBlank(message = "O token ou chave de confirmação é obrigatório.")
    private String confirmation;

    private String reason;

    public CriticalActionRequestDTO() {}

    public CriticalActionRequestDTO(String confirmation, String reason) {
        this.confirmation = confirmation;
        this.reason = reason;
    }

    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
