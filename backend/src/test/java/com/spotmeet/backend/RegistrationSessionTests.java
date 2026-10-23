package com.spotmeet.backend;

import com.spotmeet.backend.security.AuthenticationAttempts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.config.SecurityConfig;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real verification service and JWT signing with in-memory accounts only.
class RegistrationSessionTests {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
    private User user;
    private JwtUtil jwt;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("user@example.com");
        user.setRole("USER");
        user.setEmailVerified(false);
        user.setVerificationToken(encoder.encode("012345"));
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailForUpdate("user@example.com")).thenAnswer(call -> Optional.of(user));
        when(users.findByEmailForUpdate("missing@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "attempts", new AuthenticationAttempts());
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "auditLogRepo", mock(AuditLogRepository.class));
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(jwt, "expirationMs", 3_600_000L);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private JsonNode confirm() throws Exception {
        String body = mvc.perform(post("/api/auth/verify-email").contentType("application/json")
                        .content("{\"email\":\"user@example.com\",\"code\":\"012345\",\"role\":\"SYSADMIN\",\"userId\":99}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private void reject(String email, String code) throws Exception {
        mvc.perform(post("/api/auth/verify-email").contentType("application/json")
                        .content(mapper.writeValueAsBytes(java.util.Map.of("email", email, "code", code))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void confirmingConsumesOtpAndReturnsOnlyTheVerifiedAccountsSession() throws Exception {
        JsonNode response = confirm();
        assertEquals("USER", response.get("role").asText());
        assertEquals(1L, response.get("userId").asLong());
        assertEquals(user.getName(), response.get("name").asText());
        assertEquals(user.getEmail(), response.get("email").asText());
        assertTrue(response.get("emailVerified").asBoolean());
        assertTrue(jwt.isTokenValid(response.get("token").asText(), user.getEmail(), user.getTokensValidAfter()));
        assertEquals(8, response.size());
        assertNull(user.getVerificationToken());
        assertNull(user.getVerificationTokenExpiresAt());
        assertTrue(user.isEmailVerified());
    }

    @Test
    void consumedCodeCannotIssueAnotherSession() throws Exception {
        confirm();
        reject(user.getEmail(), "012345");
    }

    @Test
    void wrongCodeDoesNotActivateOrIssueSession() throws Exception {
        reject(user.getEmail(), "654321");
        assertFalse(user.isEmailVerified());
        assertNotNull(user.getVerificationToken());
    }

    @Test
    void expiredCodeDoesNotActivateOrIssueSession() throws Exception {
        user.setVerificationTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
        reject(user.getEmail(), "012345");
        assertFalse(user.isEmailVerified());
    }

    @Test
    void replacedCodeDoesNotIssueSession() throws Exception {
        user.setVerificationToken(encoder.encode("654321"));
        reject(user.getEmail(), "012345");
        assertFalse(user.isEmailVerified());
    }

    @Test
    void missingAccountAndMalformedCodeCannotIssueSession() throws Exception {
        reject("missing@example.com", "012345");
        reject(user.getEmail(), "01234");
        assertFalse(user.isEmailVerified());
    }

    @Test
    void verificationSessionRespectsTheExistingJwtCutoffAndLaterRevocation() throws Exception {
        user.setTokensValidAfter(Instant.now());
        String token = confirm().get("token").asText();
        Instant issuedAt = jwt.extractIssuedAt(token);
        assertTrue(issuedAt.isAfter(user.getTokensValidAfter()));
        assertFalse(jwt.isTokenValid(token, user.getEmail(), issuedAt.plusMillis(1)));
    }
}
