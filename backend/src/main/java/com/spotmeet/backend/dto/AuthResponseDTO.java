package com.spotmeet.backend.dto;

/**
 * Authentication response DTO.
 */
public class AuthResponseDTO {

    private String token;
    private String role;
    private String name;
    private String email;
    private Long userId;
    private boolean emailVerified;

    public AuthResponseDTO(String token, String role, String name, String email, Long userId, boolean emailVerified) {
        this.token = token;
        this.role = role;
        this.name = name;
        this.email = email;
        this.userId = userId;
        this.emailVerified = emailVerified;
    }

    // Getters only: response DTO is immutable after creation

    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Long getUserId() { return userId; }
    public boolean isEmailVerified() { return emailVerified; }
}
