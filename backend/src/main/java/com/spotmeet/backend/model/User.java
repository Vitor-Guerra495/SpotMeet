package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * User entity. Implements UserDetails for Spring Security integration.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    /** Always stored as a BCrypt hash, never plain text. */
    @Column(nullable = false)
    private String password;

    /**
     * Global role of the user in the system.
     * Values: USER | ADMIN (legacy) | SYSADMIN
     * Roles inside an organization or committee are managed by OrganizationMember and CommitteeMember.
     */
    @Column(name = "role", nullable = false)
    private String role = "USER";

    /** False by default; set to true after e-mail verification. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.PRESENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_theme", nullable = false)
    private PreferredTheme preferredTheme = PreferredTheme.DARK;

    @Column(name = "bio", length = 500)
    private String bio;

    /** E-mail verification token. */
    @Column(name = "verification_token")
    private String verificationToken;

    /** Expiration of the verification token. */
    @Column(name = "verification_token_expires_at")
    private LocalDateTime verificationTokenExpiresAt;

    /** Password recovery token. */
    @Column(name = "recovery_token")
    private String recoveryToken;

    /** Expiration of the password recovery token. */
    @Column(name = "recovery_token_expires_at")
    private LocalDateTime recoveryTokenExpiresAt;

    /** Security code sent to the current e-mail in step 1 of the e-mail change flow (LGPD). */
    @Column(name = "current_email_change_token")
    private String currentEmailChangeToken;

    @Column(name = "current_email_change_token_expires_at")
    private LocalDateTime currentEmailChangeTokenExpiresAt;

    /** New e-mail address waiting for confirmation in step 2. */
    @Column(name = "pending_new_email")
    private String pendingNewEmail;

    /** Activation code sent to the new e-mail in step 2 of the e-mail change flow (LGPD). */
    @Column(name = "new_email_token")
    private String newEmailToken;

    @Column(name = "new_email_token_expires_at")
    private LocalDateTime newEmailTokenExpiresAt;

    /*
     * The user-organization association is managed exclusively through OrganizationMember.
     * A user may belong to several organizations with different roles.
     */

    public User() {}

    // Inner enums

    public enum AvailabilityStatus {
        PRESENT, ONLINE, BUSY, AWAY
    }

    public enum PreferredTheme {
        LIGHT, DARK, SYSTEM
    }

    // UserDetails (Spring Security)

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** Spring Security uses getPassword() to compare against the BCrypt hash. */
    @Override
    public String getPassword() { return password; }

    /** The Spring Security username is the e-mail. */
    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /** Users with an unverified e-mail cannot log in. */
    @Override
    public boolean isEnabled() { return emailVerified; }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public PreferredTheme getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(PreferredTheme preferredTheme) { this.preferredTheme = preferredTheme; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public LocalDateTime getVerificationTokenExpiresAt() { return verificationTokenExpiresAt; }
    public void setVerificationTokenExpiresAt(LocalDateTime verificationTokenExpiresAt) {
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
    }

    public String getRecoveryToken() { return recoveryToken; }
    public void setRecoveryToken(String recoveryToken) { this.recoveryToken = recoveryToken; }

    public LocalDateTime getRecoveryTokenExpiresAt() { return recoveryTokenExpiresAt; }
    public void setRecoveryTokenExpiresAt(LocalDateTime recoveryTokenExpiresAt) {
        this.recoveryTokenExpiresAt = recoveryTokenExpiresAt;
    }

    public String getCurrentEmailChangeToken() { return currentEmailChangeToken; }
    public void setCurrentEmailChangeToken(String currentEmailChangeToken) {
        this.currentEmailChangeToken = currentEmailChangeToken;
    }

    public LocalDateTime getCurrentEmailChangeTokenExpiresAt() { return currentEmailChangeTokenExpiresAt; }
    public void setCurrentEmailChangeTokenExpiresAt(LocalDateTime currentEmailChangeTokenExpiresAt) {
        this.currentEmailChangeTokenExpiresAt = currentEmailChangeTokenExpiresAt;
    }

    public String getPendingNewEmail() { return pendingNewEmail; }
    public void setPendingNewEmail(String pendingNewEmail) { this.pendingNewEmail = pendingNewEmail; }

    public String getNewEmailToken() { return newEmailToken; }
    public void setNewEmailToken(String newEmailToken) { this.newEmailToken = newEmailToken; }

    public LocalDateTime getNewEmailTokenExpiresAt() { return newEmailTokenExpiresAt; }
    public void setNewEmailTokenExpiresAt(LocalDateTime newEmailTokenExpiresAt) {
        this.newEmailTokenExpiresAt = newEmailTokenExpiresAt;
    }
}
