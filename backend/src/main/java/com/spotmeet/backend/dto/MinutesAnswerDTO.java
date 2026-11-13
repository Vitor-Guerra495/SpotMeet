package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Answer to the minutes: approve, or reject with a reason. */
public class MinutesAnswerDTO {

    @NotNull(message = "Informe se aprova ou reprova a ata.")
    private Boolean approved;

    @Size(max = 300, message = "O motivo pode ter até 300 caracteres.")
    private String reason;

    public MinutesAnswerDTO() {}

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
