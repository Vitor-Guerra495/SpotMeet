package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.User;

/**
 * Internal code request result, without code or SMTP delivery state.
 */
public class CodeDeliveryResultDTO {

    private User user;
    private String destination;
    private String message;

    public CodeDeliveryResultDTO() {}

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public CodeDeliveryResultDTO(String message) {
        this.message = message;
    }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

}
