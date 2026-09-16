package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.User;

/**
 * Safe profile response DTO (LGPD compliant).
 */
public class UserProfileResponseDTO {

    private Long id;
    private String name;
    private String email;
    private String bio;
    private String availabilityStatus;
    private String preferredTheme;
    private String role;

    public UserProfileResponseDTO() {}

    public static UserProfileResponseDTO fromEntity(User user) {
        UserProfileResponseDTO dto = new UserProfileResponseDTO();
        dto.id = user.getId();
        dto.name = user.getName();
        dto.email = user.getEmail();
        dto.bio = user.getBio();
        dto.availabilityStatus = user.getAvailabilityStatus() != null ? user.getAvailabilityStatus().name() : "PRESENT";
        dto.preferredTheme = user.getPreferredTheme() != null ? user.getPreferredTheme().name() : "DARK";
        dto.role = user.getRole();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public String getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(String preferredTheme) { this.preferredTheme = preferredTheme; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
