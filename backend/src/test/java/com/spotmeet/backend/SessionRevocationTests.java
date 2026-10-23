package com.spotmeet.backend;

import com.spotmeet.backend.security.AuthenticationAttempts;

import com.spotmeet.backend.dto.ChangePasswordDTO;
import com.spotmeet.backend.dto.CriticalActionRequestDTO;
import com.spotmeet.backend.dto.ResetPasswordDTO;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.*;
import com.spotmeet.backend.service.AdminService;
import com.spotmeet.backend.service.UserService;
import com.spotmeet.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionRevocationTests {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private User user;
    private UserRepository users;
    private AuditLogRepository audit;
    private UserService service;
    private AdminService admin;
    private AccessRequestRepository requests;
    private OrganizationRepository organizations;
    private OrganizationMemberRepository members;
    private CommitteeRepository committees;
    private CommitteeMemberRepository committeeMembers;

    @BeforeEach void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("Test User");
        user.setPassword(encoder.encode("Example1"));
        user.setEmailVerified(true);
        users = mock(UserRepository.class);
        audit = mock(AuditLogRepository.class);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        service = new UserService();
        ReflectionTestUtils.setField(service, "attempts", new AuthenticationAttempts());
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "auditLogRepo", audit);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "emailService", mock(EmailService.class));
        admin = new AdminService();
        requests = mock(AccessRequestRepository.class);
        organizations = mock(OrganizationRepository.class);
        members = mock(OrganizationMemberRepository.class);
        committees = mock(CommitteeRepository.class);
        committeeMembers = mock(CommitteeMemberRepository.class);
        ReflectionTestUtils.setField(admin, "userRepo", users);
        ReflectionTestUtils.setField(admin, "auditLogRepo", audit);
        ReflectionTestUtils.setField(admin, "accessRequestRepo", requests);
        ReflectionTestUtils.setField(admin, "invitationRepo", mock(OrganizationInvitationRepository.class));
        ReflectionTestUtils.setField(admin, "organizationRepo", organizations);
        ReflectionTestUtils.setField(admin, "organizationMemberRepo", members);
        ReflectionTestUtils.setField(admin, "committeeRepo", committees);
        ReflectionTestUtils.setField(admin, "committeeMemberRepo", committeeMembers);
    }

    private ChangePasswordDTO change(String current, String next) {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword(current);
        dto.setNewPassword(next);
        return dto;
    }
    private ResetPasswordDTO reset() {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setEmail(user.getEmail());
        dto.setToken("012345");
        dto.setNewPassword("Another2");
        return dto;
    }
    private CriticalActionRequestDTO fullReset(String confirmation) {
        CriticalActionRequestDTO dto = new CriticalActionRequestDTO();
        dto.setConfirmation(confirmation);
        dto.setReason("QA isolated reset");
        return dto;
    }
    private void recoveryCode() {
        user.setRecoveryToken(encoder.encode("012345"));
        user.setRecoveryTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
    }

    @Test void passwordChangeWritesCutoffAndHashTogetherForTheAuthenticatedUser() {
        Instant before = Instant.now().minusMillis(1);
        service.changePassword(user.getEmail(), change("Example1", "Another2"));
        assertNotNull(user.getTokensValidAfter());
        assertTrue(user.getTokensValidAfter().isAfter(before));
        assertTrue(encoder.matches("Another2", user.getPassword()));
        verify(users).findByEmailForUpdate(user.getEmail());
        verify(users).save(user);
        verify(users, never()).findByEmail(anyString());
    }
    @Test void wrongCurrentPasswordKeepsTheSessionCutoffAndHash() {
        String previousHash = user.getPassword();
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(user.getEmail(), change("Wrong1", "Another2")));
        assertNull(user.getTokensValidAfter());
        assertEquals(previousHash, user.getPassword());
        verify(users, never()).save(any());
    }
    @Test void unchangedPasswordDoesNotRevokeSessions() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(user.getEmail(), change("Example1", "Example1")));
        assertNull(user.getTokensValidAfter());
        verify(users, never()).save(any());
    }
    @Test void successfulRecoveryConsumesTheOtpAndRevokesSessions() {
        recoveryCode();
        service.resetPassword(reset());
        assertNotNull(user.getTokensValidAfter());
        assertNull(user.getRecoveryToken());
        assertNull(user.getRecoveryTokenExpiresAt());
        assertTrue(encoder.matches("Another2", user.getPassword()));
        verify(users).save(user);
    }
    @Test void recoveryVerificationAloneDoesNotRevokeSessions() {
        recoveryCode();
        com.spotmeet.backend.dto.VerifyRecoveryCodeDTO dto = new com.spotmeet.backend.dto.VerifyRecoveryCodeDTO();
        dto.setEmail(user.getEmail());
        dto.setToken("012345");
        service.verifyRecoveryCode(dto);
        assertNull(user.getTokensValidAfter());
        assertNotNull(user.getRecoveryToken());
        verify(users, never()).save(any());
    }
    @Test void invalidRecoveryCodeDoesNotRevokeSessions() {
        recoveryCode();
        ResetPasswordDTO dto = reset();
        dto.setToken("654321");
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(dto));
        assertNull(user.getTokensValidAfter());
        assertTrue(encoder.matches("Example1", user.getPassword()));
        verify(users, never()).save(any());
    }
    @Test void expiredRecoveryCodeDoesNotRevokeSessions() {
        recoveryCode();
        user.setRecoveryTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(reset()));
        assertNull(user.getTokensValidAfter());
        verify(users, never()).save(any());
    }
    @Test void fullResetRevokesThePreservedAdministratorWithoutChangingItsPassword() {
        user.setRole("SYSADMIN");
        String previousHash = user.getPassword();
        var result = admin.resetSystem(fullReset("CONFIRM_FULL_RESET"), user.getEmail());
        assertEquals(true, result.get("success"));
        assertNotNull(user.getTokensValidAfter());
        assertEquals(previousHash, user.getPassword());
        verify(users).findByEmailForUpdate(user.getEmail());
        verify(users).deleteAllExceptAdmin(user.getId());
        verify(users).save(user);
        verify(organizations).deleteAllInBatch();
        verify(committees).deleteAllInBatch();
    }
    @Test void invalidResetConfirmationDoesNotDeleteDataOrRevokeSessions() {
        user.setRole("SYSADMIN");
        assertThrows(IllegalArgumentException.class,
                () -> admin.resetSystem(fullReset("WRONG"), user.getEmail()));
        assertNull(user.getTokensValidAfter());
        verify(users, never()).deleteAllExceptAdmin(any());
        verify(users, never()).save(any());
        verifyNoInteractions(organizations, committees, requests, members, committeeMembers, audit);
    }
    @Test void regularMemberCannotResetOrRevokeOtherSessions() {
        assertThrows(SecurityException.class,
                () -> admin.resetSystem(fullReset("CONFIRM_FULL_RESET"), user.getEmail()));
        assertNull(user.getTokensValidAfter());
        verify(users, never()).deleteAllExceptAdmin(any());
        verify(users, never()).save(any());
    }
    @Test void cleanupFailurePreventsSuccessfulResetAndCutoffWrite() {
        user.setRole("SYSADMIN");
        doThrow(new IllegalStateException("Simulated cleanup failure")).when(organizations).deleteAllInBatch();
        assertThrows(IllegalStateException.class,
                () -> admin.resetSystem(fullReset("CONFIRM_FULL_RESET"), user.getEmail()));
        assertNull(user.getTokensValidAfter());
        verify(users, never()).save(any());
    }
}
