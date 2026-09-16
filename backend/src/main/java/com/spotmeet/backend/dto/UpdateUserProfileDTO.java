package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating profile information: name, bio, availability and theme.
 */
public class UpdateUserProfileDTO {

    @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.")
    private String name;

    @Size(max = 500, message = "A biografia pode ter no máximo 500 caracteres.")
    private String bio;

    /** Accepted values: PRESENT | ONLINE | BUSY | AWAY */
    private String availabilityStatus;

    /** Accepted values: LIGHT | DARK | SYSTEM */
    private String preferredTheme;

    public UpdateUserProfileDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public String getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(String preferredTheme) { this.preferredTheme = preferredTheme; }
}
