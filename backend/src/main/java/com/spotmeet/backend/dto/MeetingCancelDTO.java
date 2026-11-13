package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional reason of a cancellation, shown in the e-mail sent to the guests. */
public class MeetingCancelDTO {

    @Size(max = 300, message = "O motivo pode ter até 300 caracteres.")
    private String reason;

    public MeetingCancelDTO() {}

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
