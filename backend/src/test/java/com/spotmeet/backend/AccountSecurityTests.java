package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.controller.UserController;
import com.spotmeet.backend.dto.ForgotPasswordDTO;
import com.spotmeet.backend.dto.ResendVerificationDTO;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.AuthenticationAttempts;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.EmailService;
import com.spotmeet.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

class AccountSecurityTests {
    enum Flow { LOGIN, PASSWORD, VERIFICATION, RECOVERY, CURRENT_EMAIL, NEW_EMAIL }

    private static final String EMAIL = "user@example.com";
    private static final String HASH = "$2a$12$" + "a".repeat(53);
    private static final ObjectMapper JSON = new ObjectMapper();
    private User user;
    private UserService service;
    private UserRepository users;
    private PasswordEncoder encoder;
    private EmailService mail;
    private AuthenticationManager manager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail(EMAIL);
        user.setName("Test User");
        user.setRole("USER");
        user.setPassword(HASH);
        user.setVerificationToken(HASH);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        user.setRecoveryToken(HASH);
        user.setRecoveryTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        user.setCurrentEmailChangeToken(HASH);
        user.setCurrentEmailChangeTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        user.setPendingNewEmail("new@example.com");
        user.setNewEmailToken(HASH);
        user.setNewEmailTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        users = mock(UserRepository.class);
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn(HASH);
        when(encoder.matches(anyString(), anyString())).thenAnswer(call -> HASH.equals(call.getArgument(1))
                && ("012345".equals(call.getArgument(0)) || "OldPassword1".equals(call.getArgument(0))));
        mail = mock(EmailService.class);
        service = new UserService();
        var attempts = new AuthenticationAttempts();
        ReflectionTestUtils.setField(service, "attempts", attempts);
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "emailService", mail);
        ReflectionTestUtils.setField(service, "auditLogRepo", mock(AuditLogRepository.class));
        manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenAnswer(call -> {
            var credentials = call.getArgument(0, org.springframework.security.core.Authentication.class);
            if (!EMAIL.equals(credentials.getName()) || !"OldPassword1".equals(credentials.getCredentials())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        });
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.extractIssuedAt(anyString())).thenReturn(Instant.now());
        when(jwt.generateToken(anyString(), anyString(), any())).thenReturn("test-session");
        AuthController auth = new AuthController();
        ReflectionTestUtils.setField(auth, "attempts", attempts);
        ReflectionTestUtils.setField(auth, "authenticationManager", manager);
        ReflectionTestUtils.setField(auth, "userService", service);
        ReflectionTestUtils.setField(auth, "jwtUtil", jwt);
        UserController profile = new UserController();
        ReflectionTestUtils.setField(profile, "userService", service);
        ReflectionTestUtils.setField(profile, "jwtUtil", jwt);
        mvc = MockMvcBuilders.standaloneSetup(auth, profile)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private MvcResult submit(Flow flow, boolean valid) throws Exception {
        return submit(flow, valid, EMAIL, false);
    }

    private MvcResult submit(Flow flow, boolean valid, String email, boolean reset) throws Exception {
        String code = valid ? "012345" : "654321";
        String path = switch (flow) {
            case LOGIN -> "/api/auth/login";
            case PASSWORD -> "/api/users/me/password";
            case VERIFICATION -> "/api/auth/verify-email";
            case RECOVERY -> reset ? "/api/auth/reset-password" : "/api/auth/verify-recovery-code";
            case CURRENT_EMAIL -> "/api/users/me/email-change/confirm";
            case NEW_EMAIL -> "/api/users/me/email-change/complete";
        };
        Map<String, String> body = switch (flow) {
            case LOGIN -> Map.of("email", email, "password", valid ? "OldPassword1" : "WrongPassword1");
            case PASSWORD -> Map.of("currentPassword", valid ? "OldPassword1" : "WrongPassword1", "newPassword", "NewPassword2");
            case VERIFICATION -> Map.of("email", email, "code", code);
            case RECOVERY -> reset ? Map.of("email", email, "token", code, "newPassword", "NewPassword2") : Map.of("email", email, "token", code);
            case CURRENT_EMAIL -> Map.of("currentCode", code, "newEmail", "new@example.com");
            case NEW_EMAIL -> Map.of("newEmailCode", code);
        };
        return mvc.perform(request(flow == Flow.PASSWORD ? HttpMethod.PUT : HttpMethod.POST, path)
                .principal(new UsernamePasswordAuthenticationToken(EMAIL, null))
                .header("Authorization", "Bearer test-session")
                .header("X-Forwarded-For", "192.0.2." + (valid ? 2 : 1))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsBytes(body))).andReturn();
    }

    private void assertPaused(MvcResult result) throws Exception {
        assertEquals(429, result.getResponse().getStatus());
        int retryAfter = Integer.parseInt(result.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter >= 899 && retryAfter <= 900);
        var body = JSON.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("TOO_MANY_ATTEMPTS", body.get("error").asText());
        assertEquals(retryAfter, body.get("retryAfterSeconds").asInt());
        assertFalse(body.get("success").asBoolean());
        assertFalse(body.toString().contains(EMAIL));
        assertFalse(body.toString().contains("654321"));
    }

    @ParameterizedTest
    @EnumSource(Flow.class)
    void tenFailuresPauseEveryCredentialFlowAndPreserveAccountData(Flow flow) throws Exception {
        for (int i = 0; i < 9; i++) assertEquals(flow == Flow.LOGIN ? 401 : 400, submit(flow, false).getResponse().getStatus());
        assertPaused(submit(flow, false));
        assertPaused(submit(flow, true));
        assertEquals(HASH, user.getPassword());
        assertEquals(HASH, user.getVerificationToken());
        assertEquals(HASH, user.getRecoveryToken());
        assertEquals(HASH, user.getCurrentEmailChangeToken());
        assertEquals(HASH, user.getNewEmailToken());
        assertNull(user.getTokensValidAfter());
        verify(users, never()).save(any());
        verifyNoInteractions(mail);
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void bothRecoveryEndpointsShareTheSameBudget() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(400, submit(Flow.RECOVERY, false).getResponse().getStatus());
        for (int i = 0; i < 4; i++) assertEquals(400, submit(Flow.RECOVERY, false, EMAIL, true).getResponse().getStatus());
        assertPaused(submit(Flow.RECOVERY, false, EMAIL, true));
        assertPaused(submit(Flow.RECOVERY, true));
        verifyNoInteractions(mail);
    }

    @Test
    void unknownLoginAccountsUseTheSamePolicyAndCaseDoesNotBypassIt() throws Exception {
        for (int i = 0; i < 9; i++) assertEquals(401, submit(Flow.LOGIN, false, "missing@example.com", false).getResponse().getStatus());
        assertPaused(submit(Flow.LOGIN, false, "MISSING@EXAMPLE.COM", false));
        verify(manager, times(10)).authenticate(any());
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"VERIFICATION", "RECOVERY", "CURRENT_EMAIL", "NEW_EMAIL"})
    void resendingAnOtpDoesNotClearFailedGuesses(Flow flow) throws Exception {
        for (int i = 0; i < 9; i++) assertEquals(400, submit(flow, false).getResponse().getStatus());
        switch (flow) {
            case VERIFICATION -> { var dto = new ResendVerificationDTO(); dto.setEmail(EMAIL); service.resendVerification(dto); }
            case RECOVERY -> { var dto = new ForgotPasswordDTO(); dto.setEmail(EMAIL); service.forgotPassword(dto); }
            case CURRENT_EMAIL -> service.requestEmailChange(EMAIL);
            case NEW_EMAIL -> service.resendNewEmailCode(EMAIL);
            default -> throw new AssertionError("Unexpected flow");
        }
        assertPaused(submit(flow, false));
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"PASSWORD", "RECOVERY"})
    void passwordPunctuationReachesTheEncoderUnchanged(Flow flow) throws Exception {
        String password = "Xss<>&'\"Pass1";
        var body = flow == Flow.PASSWORD
                ? Map.of("currentPassword", "OldPassword1", "newPassword", password)
                : Map.of("email", EMAIL, "token", "012345", "newPassword", password);
        var result = mvc.perform(request(flow == Flow.PASSWORD ? HttpMethod.PUT : HttpMethod.POST,
                        flow == Flow.PASSWORD ? "/api/users/me/password" : "/api/auth/reset-password")
                .principal(new UsernamePasswordAuthenticationToken(EMAIL, null))
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(body))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        verify(encoder).encode(password);
        verify(mail).sendPasswordChangedEmail(EMAIL);
        assertNotNull(user.getTokensValidAfter());
    }
}
