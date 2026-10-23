package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.config.SecurityConfig;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.controller.UserController;
import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.service.EmailService;
import com.spotmeet.backend.service.UserService;
import com.spotmeet.backend.security.AuthenticationAttempts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Real service and BCrypt, with in-memory accounts and a simulated mail sender.
class OtpSecurityTests {
    enum Purpose { VERIFICATION, RECOVERY, CURRENT_EMAIL, NEW_EMAIL }

    private static final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
    private static final String EMAIL = "user@example.com";
    private static final String NEW_EMAIL = "new@example.com";
    private final Map<String, User> accounts = new HashMap<>();
    private final Map<String, String> sentCodes = new HashMap<>();
    private UserService service;
    private UserRepository users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        when(users.findByEmailForUpdate(anyString())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0))));
        when(users.existsByEmail(anyString())).thenAnswer(call -> accounts.containsKey(call.getArgument(0)));
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            accounts.entrySet().removeIf(entry -> entry.getValue() == user);
            accounts.put(user.getEmail(), user);
            return user;
        });
        EmailService mail = mock(EmailService.class);
        doAnswer(call -> capture(Purpose.VERIFICATION, call.getArgument(0), call.getArgument(2))).when(mail).sendVerificationEmail(anyString(), anyString(), anyString(), any());
        doAnswer(call -> capture(Purpose.RECOVERY, call.getArgument(0), call.getArgument(2))).when(mail).sendPasswordRecoveryEmail(anyString(), anyString(), anyString(), any());
        doAnswer(call -> capture(Purpose.CURRENT_EMAIL, call.getArgument(0), call.getArgument(2))).when(mail).sendCurrentEmailChangeCode(anyString(), anyString(), anyString(), any());
        doAnswer(call -> capture(Purpose.NEW_EMAIL, call.getArgument(0), call.getArgument(2))).when(mail).sendNewEmailConfirmationCode(anyString(), anyString(), anyString(), any());
        service = new UserService();
        ReflectionTestUtils.setField(service, "attempts", new AuthenticationAttempts());
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "auditLogRepo", mock(AuditLogRepository.class));
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "emailService", mail);
        SecureRandom random = mock(SecureRandom.class);
        AtomicInteger nextCode = new AtomicInteger(100000);
        when(random.nextInt(1_000_000)).thenAnswer(call -> nextCode.getAndIncrement());
        ReflectionTestUtils.setField(service, "secureRandom", random);
        AuthController auth = new AuthController();
        UserController profile = new UserController();
        ReflectionTestUtils.setField(auth, "userService", service);
        ReflectionTestUtils.setField(profile, "userService", service);
        mvc = MockMvcBuilders.standaloneSetup(auth, profile).build();
    }

    private Void capture(Purpose purpose, String email, String code) {
        sentCodes.put(purpose + ":" + email, code);
        return null;
    }

    private User prepare(Purpose purpose) {
        RegisterRequestDTO dto = new RegisterRequestDTO();
        dto.setName("Test User");
        dto.setEmail(EMAIL);
        dto.setPassword("Example1");
        User user = service.registerUser(dto).getUser();
        if (purpose == Purpose.RECOVERY) service.forgotPassword(new ForgotPasswordDTO(EMAIL));
        if (purpose == Purpose.CURRENT_EMAIL || purpose == Purpose.NEW_EMAIL) service.requestEmailChange(EMAIL);
        if (purpose == Purpose.NEW_EMAIL) service.confirmEmailChange(EMAIL, new ConfirmEmailChangeDTO(code(Purpose.CURRENT_EMAIL), NEW_EMAIL));
        return user;
    }

    private String code(Purpose purpose) {
        return sentCodes.get(purpose + ":" + (purpose == Purpose.NEW_EMAIL ? NEW_EMAIL : EMAIL));
    }

    private String hash(User user, Purpose purpose) {
        return switch (purpose) {
            case VERIFICATION -> user.getVerificationToken();
            case RECOVERY -> user.getRecoveryToken();
            case CURRENT_EMAIL -> user.getCurrentEmailChangeToken();
            case NEW_EMAIL -> user.getNewEmailToken();
        };
    }

    private LocalDateTime expiry(User user, Purpose purpose) {
        return switch (purpose) {
            case VERIFICATION -> user.getVerificationTokenExpiresAt();
            case RECOVERY -> user.getRecoveryTokenExpiresAt();
            case CURRENT_EMAIL -> user.getCurrentEmailChangeTokenExpiresAt();
            case NEW_EMAIL -> user.getNewEmailTokenExpiresAt();
        };
    }

    private void setExpiry(User user, Purpose purpose, LocalDateTime expiry) {
        switch (purpose) {
            case VERIFICATION -> user.setVerificationTokenExpiresAt(expiry);
            case RECOVERY -> user.setRecoveryTokenExpiresAt(expiry);
            case CURRENT_EMAIL -> user.setCurrentEmailChangeTokenExpiresAt(expiry);
            case NEW_EMAIL -> user.setNewEmailTokenExpiresAt(expiry);
        }
    }

    private void setHash(User user, Purpose purpose, String hash) {
        switch (purpose) {
            case VERIFICATION -> user.setVerificationToken(hash);
            case RECOVERY -> user.setRecoveryToken(hash);
            case CURRENT_EMAIL -> user.setCurrentEmailChangeToken(hash);
            case NEW_EMAIL -> user.setNewEmailToken(hash);
        }
    }

    private void consume(Purpose purpose, String email, String code) {
        switch (purpose) {
            case VERIFICATION -> service.verifyEmail(new VerifyEmailDTO(email, code));
            case RECOVERY -> service.resetPassword(new ResetPasswordDTO(email, code, "Another2"));
            case CURRENT_EMAIL -> service.confirmEmailChange(email, new ConfirmEmailChangeDTO(code, NEW_EMAIL));
            case NEW_EMAIL -> service.completeEmailChange(email, new CompleteEmailChangeDTO(code));
        }
    }

    private void resend(Purpose purpose) {
        switch (purpose) {
            case VERIFICATION -> service.resendVerification(new ResendVerificationDTO(EMAIL));
            case RECOVERY -> service.forgotPassword(new ForgotPasswordDTO(EMAIL));
            case CURRENT_EMAIL -> service.requestEmailChange(EMAIL);
            case NEW_EMAIL -> service.resendNewEmailCode(EMAIL);
        }
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void storesOnlyBcryptWithFiveMinuteExpiry(Purpose purpose) {
        LocalDateTime start = LocalDateTime.now();
        User user = prepare(purpose);
        assertTrue(code(purpose).matches("[0-9]{6}"));
        assertNotEquals(code(purpose), hash(user, purpose));
        assertTrue(hash(user, purpose).startsWith("$2a$12$"));
        assertEquals(60, hash(user, purpose).length());
        assertTrue(encoder.matches(code(purpose), hash(user, purpose)));
        assertFalse(expiry(user, purpose).isBefore(start.plusMinutes(5)));
        assertFalse(expiry(user, purpose).isAfter(LocalDateTime.now().plusMinutes(5)));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void successfulUseConsumesTheCode(Purpose purpose) {
        User user = prepare(purpose);
        String code = code(purpose);
        consume(purpose, EMAIL, code);
        assertNull(hash(user, purpose));
        assertNull(expiry(user, purpose));
        String email = purpose == Purpose.NEW_EMAIL ? NEW_EMAIL : EMAIL;
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, email, code));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void resendingInvalidatesThePreviousCode(Purpose purpose) {
        User user = prepare(purpose);
        String oldCode = code(purpose);
        resend(purpose);
        assertNotEquals(oldCode, code(purpose));
        assertFalse(encoder.matches(oldCode, hash(user, purpose)));
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, EMAIL, oldCode));
        consume(purpose, EMAIL, code(purpose));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void incorrectCodeDoesNotConsumeTheValidOne(Purpose purpose) {
        User user = prepare(purpose);
        String hash = hash(user, purpose);
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, EMAIL, "999999"));
        assertEquals(hash, hash(user, purpose));
        consume(purpose, EMAIL, code(purpose));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void rejectsExpiredCodes(Purpose purpose) {
        User user = prepare(purpose);
        setExpiry(user, purpose, LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, EMAIL, code(purpose)));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void rejectsCodesWithoutExpiry(Purpose purpose) {
        User user = prepare(purpose);
        setExpiry(user, purpose, null);
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, EMAIL, code(purpose)));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void rejectsLegacyPlaintextCodes(Purpose purpose) {
        User user = prepare(purpose);
        setHash(user, purpose, code(purpose));
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, EMAIL, code(purpose)));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void anotherAccountCannotUseTheCode(Purpose purpose) {
        prepare(purpose);
        User other = new User();
        other.setEmail("other@example.com");
        accounts.put(other.getEmail(), other);
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, other.getEmail(), code(purpose)));
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, "missing@example.com", code(purpose)));
        assertThrows(IllegalArgumentException.class, () -> consume(purpose, null, code(purpose)));
    }

    @Test
    void recoveryCheckDoesNotConsumeBeforePasswordReset() {
        User user = prepare(Purpose.RECOVERY);
        String hash = user.getRecoveryToken();
        VerifyRecoveryCodeDTO dto = new VerifyRecoveryCodeDTO(EMAIL, code(Purpose.RECOVERY));
        service.verifyRecoveryCode(dto);
        service.verifyRecoveryCode(dto);
        assertEquals(hash, user.getRecoveryToken());
        consume(Purpose.RECOVERY, EMAIL, code(Purpose.RECOVERY));
        assertTrue(encoder.matches("Another2", user.getPassword()));
        assertThrows(IllegalArgumentException.class, () -> service.verifyRecoveryCode(dto));
    }

    @Test
    void restartingEmailChangeRevokesThePreviouslyApprovedDestination() {
        User user = prepare(Purpose.NEW_EMAIL);
        String oldCode = code(Purpose.NEW_EMAIL);
        service.requestEmailChange(EMAIL);
        assertNull(user.getPendingNewEmail());
        assertNull(user.getNewEmailToken());
        assertNull(user.getNewEmailTokenExpiresAt());
        assertThrows(IllegalArgumentException.class, () -> service.resendNewEmailCode(EMAIL));
        assertThrows(IllegalArgumentException.class, () -> consume(Purpose.NEW_EMAIL, EMAIL, oldCode));
    }

    @Test
    void resendDoesNotGenerateTheSameNumericCodeEvenIfRandomRepeats() {
        prepare(Purpose.RECOVERY);
        String oldCode = code(Purpose.RECOVERY);
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(Integer.parseInt(oldCode), Integer.parseInt(oldCode), 654321);
        ReflectionTestUtils.setField(service, "secureRandom", random);
        service.forgotPassword(new ForgotPasswordDTO(EMAIL));
        assertNotEquals(oldCode, code(Purpose.RECOVERY));
        verify(random, times(3)).nextInt(1_000_000);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publicDispatchResponsesDoNotExposeAccountState(boolean verified) throws Exception {
        prepare(Purpose.RECOVERY).setEmailVerified(verified);
        ObjectMapper mapper = new ObjectMapper();
        for (String route : new String[]{"forgot-password", "resend-verification"}) {
            String existing = mvc.perform(post("/api/auth/" + route).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("email", EMAIL)))).andReturn().getResponse().getContentAsString();
            String missing = mvc.perform(post("/api/auth/" + route).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("email", "missing@example.com")))).andReturn().getResponse().getContentAsString();
            assertEquals(mapper.readTree(existing), mapper.readTree(missing));
            assertEquals(mapper.readTree("{\"success\":true,\"message\":\"Verifique o seu e-mail.\"}"), mapper.readTree(existing));
        }
    }

    @Test
    void theDummyComparisonDoesNotExcludeAnAllZeroFirstCode() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(0, 42);
        ReflectionTestUtils.setField(service, "secureRandom", random);
        RegisterRequestDTO dto = new RegisterRequestDTO();
        dto.setName("Test User");
        dto.setEmail(EMAIL);
        dto.setPassword("Example1");
        service.registerUser(dto);
        assertEquals("000000", code(Purpose.VERIFICATION));
        verify(random, times(1)).nextInt(1_000_000);
    }

    @Test
    void replacingLegacyPlaintextAlsoAvoidsRepeatingItsNumber() {
        User user = prepare(Purpose.RECOVERY);
        String oldCode = code(Purpose.RECOVERY);
        user.setRecoveryToken(oldCode);
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(Integer.parseInt(oldCode), 654321);
        ReflectionTestUtils.setField(service, "secureRandom", random);
        service.forgotPassword(new ForgotPasswordDTO(EMAIL));
        assertNotEquals(oldCode, code(Purpose.RECOVERY));
        assertThrows(IllegalArgumentException.class, () -> consume(Purpose.RECOVERY, EMAIL, oldCode));
        consume(Purpose.RECOVERY, EMAIL, code(Purpose.RECOVERY));
    }

    @Test
    void authenticatedResendUsesOnlyTheActorsPreviouslyApprovedDestination() throws Exception {
        prepare(Purpose.NEW_EMAIL);
        String response = mvc.perform(post("/api/users/me/email-change/resend-new")
                .principal(new UsernamePasswordAuthenticationToken(EMAIL, null))
                .contentType("application/json").content("{\"newEmail\":\"attacker@example.com\",\"userId\":999}"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains(NEW_EMAIL));
        assertFalse(response.contains("attacker@example.com"));
        assertNotNull(code(Purpose.NEW_EMAIL));
        assertFalse(response.contains(code(Purpose.NEW_EMAIL)));
        assertEquals(401, mvc.perform(post("/api/users/me/email-change/resend-new")).andReturn().getResponse().getStatus());
    }

    @Test
    void registrationAndEmailChangeResponsesContainNoCodeOrHash() throws Exception {
        String registration = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"Test User\",\"email\":\"user@example.com\",\"password\":\"Example1\"}"))
                .andReturn().getResponse().getContentAsString();
        String current = mvc.perform(post("/api/users/me/email-change/request")
                .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andReturn().getResponse().getContentAsString();
        String next = mvc.perform(post("/api/users/me/email-change/confirm")
                .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)).contentType("application/json")
                .content(new ObjectMapper().writeValueAsString(Map.of("currentCode", code(Purpose.CURRENT_EMAIL), "newEmail", NEW_EMAIL))))
                .andReturn().getResponse().getContentAsString();
        for (String response : new String[]{registration, current, next}) {
            assertFalse(response.contains("devCode"));
            assertFalse(response.contains("warning"));
            assertFalse(response.contains("$2a$"));
            sentCodes.values().forEach(code -> assertFalse(response.contains(code)));
        }
    }
}
