package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.User;

/**
 * Result of an OTP code dispatch (e-mail or SMS), indicating whether the
 * delivery happened through an external provider or fell back to local mode.
 */
public class CodeDeliveryResultDTO {

    private User user;
    private boolean delivered;
    private String channel; // "EMAIL" | "SMS"
    private String destination;
    private String otpCode;
    private String message;
    private String warning;

    public CodeDeliveryResultDTO() {}

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public CodeDeliveryResultDTO(boolean delivered, String channel, String destination, String otpCode, String message, String warning) {
        this.delivered = delivered;
        this.channel = channel;
        this.destination = destination;
        this.otpCode = otpCode;
        this.message = message;
        this.warning = warning;
    }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
