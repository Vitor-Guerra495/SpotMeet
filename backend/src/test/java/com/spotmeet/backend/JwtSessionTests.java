package com.spotmeet.backend;

import com.spotmeet.backend.security.AuthenticationAttempts;

import com.spotmeet.backend.config.SecurityConfig;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.controller.UserController;
import com.spotmeet.backend.dto.CompleteEmailChangeDTO;
import com.spotmeet.backend.dto.LoginRequestDTO;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.security.JwtAuthFilter;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtSessionTests {
    private static final byte[] KEY = "spotmeet-session-test-key-32bytes!".getBytes(StandardCharsets.UTF_8);
    private static final Map<String, User> users = new HashMap<>();
    private static AnnotationConfigWebApplicationContext context;
    private static JwtUtil jwt;
    private static MockMvc mvc;
    private static boolean databaseUnavailable;
    private User user;

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean UserDetailsService users() {
            return email -> {
                if (databaseUnavailable) throw new org.springframework.dao.DataAccessResourceFailureException("Simulated outage");
                User user = users.get(email);
                if (user == null) throw new UsernameNotFoundException(email);
                return user;
            };
        }
        @Bean ProbeController controller() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/session-test") Map<String, Boolean> profile() { return Map.of("success", true); }
        @GetMapping("/api/admin/session-test") Map<String, Boolean> admin() { return Map.of("success", true); }
        @GetMapping("/api/auth/session-test") Map<String, Boolean> publicRoute() { return Map.of("success", true); }
    }

    @BeforeAll static void startSecurity() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "spotmeet.jwt.secret=" + Base64.getEncoder().encodeToString(KEY),
                "spotmeet.jwt.expiration-ms=86400000");
        context.register(TestConfig.class, SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class);
        context.refresh();
        jwt = context.getBean(JwtUtil.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll static void closeSecurity() { context.close(); }

    @BeforeEach void account() {
        users.clear();
        databaseUnavailable = false;
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("Test User");
        user.setEmailVerified(true);
        users.put(user.getEmail(), user);
    }

    private String legacy(Instant issuedAt, Instant expiresAt) {
        var builder = Jwts.builder().subject(user.getEmail()).claim("role", "USER");
        if (issuedAt != null) builder.issuedAt(Date.from(issuedAt));
        if (expiresAt != null) builder.expiration(Date.from(expiresAt));
        return builder.signWith(Keys.hmacShaKeyFor(KEY)).compact();
    }

    private void assertStatus(String token, int status) throws Exception {
        mvc.perform(get("/api/session-test").header("Authorization", "Bearer " + token))
                .andExpect(status().is(status));
    }

    @Test void missingCredentialsAre401() throws Exception {
        mvc.perform(get("/api/session-test")).andExpect(status().isUnauthorized());
    }
    @Test void wrongHeaderIs401() throws Exception {
        mvc.perform(get("/api/session-test").header("Authorization", "Basic test"))
                .andExpect(status().isUnauthorized());
    }
    @Test void malformedTokenIs401() throws Exception { assertStatus("invalid-test-jwt", 401); }
    @Test void expiredTokenIs401() throws Exception {
        assertStatus(legacy(Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)), 401);
    }
    @Test void wrongSignatureIs401() throws Exception {
        String token = Jwts.builder().subject(user.getEmail()).issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor("different-session-test-key-32bytes".getBytes(StandardCharsets.UTF_8))).compact();
        assertStatus(token, 401);
    }
    @Test void missingAccountIs401() throws Exception {
        String token = jwt.generateToken(user.getEmail(), "USER");
        users.clear();
        assertStatus(token, 401);
    }
    @Test void databaseOutageIs503InsteadOfSessionRevocation() throws Exception {
        String token = jwt.generateToken(user.getEmail(), "USER");
        databaseUnavailable = true;
        assertStatus(token, 503);
    }
    @Test void unverifiedAccountIs401() throws Exception {
        user.setEmailVerified(false);
        assertStatus(jwt.generateToken(user.getEmail(), "USER"), 401);
    }
    @Test void nullCutoffKeepsExistingSessionsCompatible() throws Exception {
        assertStatus(legacy(Instant.now().minusSeconds(20), Instant.now().plusSeconds(60)), 200);
        assertStatus(jwt.generateToken(user.getEmail(), "USER"), 200);
    }
    @Test void revokesLegacyTokenFromTheSameSecond() throws Exception {
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(2).plusMillis(500);
        String token = legacy(cutoff.minusMillis(10), Instant.now().plusSeconds(60));
        user.setTokensValidAfter(cutoff);
        assertStatus(token, 401);
    }
    @Test void revokesBothPreciseSessionsFromTheSameSecond() throws Exception {
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(2).plusMillis(500);
        String first = jwt.generateToken(user.getEmail(), "USER", cutoff.minusMillis(100));
        String second = jwt.generateToken(user.getEmail(), "USER", cutoff.minusMillis(1));
        user.setTokensValidAfter(cutoff);
        assertStatus(first, 401);
        assertStatus(second, 401);
    }
    @Test void cutoffIsInclusiveAndNewSessionsAreAccepted() throws Exception {
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(2);
        user.setTokensValidAfter(cutoff);
        assertStatus(jwt.generateToken(user.getEmail(), "USER", cutoff), 401);
        assertStatus(jwt.generateToken(user.getEmail(), "USER", cutoff.plusMillis(1)), 200);
    }
    @Test void missingIssueDateAndExpirationAreRejected() throws Exception {
        assertStatus(legacy(null, Instant.now().plusSeconds(60)), 401);
        assertStatus(legacy(Instant.now(), null), 401);
    }
    @Test void inconsistentPreciseClaimIsRejected() throws Exception {
        String token = Jwts.builder().subject(user.getEmail()).issuedAt(new Date())
                .claim("issuedAtMillis", Instant.now().minusSeconds(20).toEpochMilli())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(KEY)).compact();
        assertStatus(token, 401);
    }
    @Test void malformedPreciseClaimIsRejected() throws Exception {
        String token = Jwts.builder().subject(user.getEmail()).issuedAt(new Date())
                .claim("issuedAtMillis", "invalid-date")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(KEY)).compact();
        assertStatus(token, 401);
    }
    @Test void authenticationAndPermissionRemainDistinct() throws Exception {
        String token = jwt.generateToken(user.getEmail(), "SYSADMIN");
        mvc.perform(get("/api/admin/session-test").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        user.setRole("SYSADMIN");
        mvc.perform(get("/api/admin/session-test").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
    @Test void publicRoutesDoNotRequireASession() throws Exception {
        mvc.perform(get("/api/auth/session-test")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/session-test").header("Authorization", "Bearer invalid-test-jwt"))
                .andExpect(status().isOk());
    }
    @Test void corsPreflightStillWorks() throws Exception {
        mvc.perform(options("/api/session-test").header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk()).andExpect(header().exists("Access-Control-Allow-Origin"));
    }
    @Test void revocationAffectsOnlyTheTargetAccount() throws Exception {
        User other = new User();
        other.setEmail("other@example.com");
        other.setEmailVerified(true);
        users.put(other.getEmail(), other);
        Instant issuedAt = Instant.now().minusSeconds(2);
        String targetToken = jwt.generateToken(user.getEmail(), "USER", issuedAt);
        String otherToken = jwt.generateToken(other.getEmail(), "USER", issuedAt);
        user.setTokensValidAfter(Instant.now());
        assertStatus(targetToken, 401);
        assertStatus(otherToken, 200);
    }
    @Test void loginBegunBeforePasswordChangeCannotCreateAValidSession() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "attempts", new AuthenticationAttempts());
        AuthenticationManager manager = mock(AuthenticationManager.class);
        ReflectionTestUtils.setField(controller, "authenticationManager", manager);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        Instant[] changedAt = new Instant[1];
        when(manager.authenticate(any())).thenAnswer(call -> {
            changedAt[0] = Instant.now().plusMillis(1);
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        });
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setEmail(user.getEmail());
        dto.setPassword("Example1");
        ResponseEntity<?> result = controller.login(dto);
        String token = ((com.spotmeet.backend.dto.AuthResponseDTO) result.getBody()).getToken();
        assertFalse(jwt.isTokenValid(token, user.getEmail(), changedAt[0]));
    }
    @Test void newLoginAfterRevocationCanIssueInTheSameMillisecond() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "attempts", new AuthenticationAttempts());
        AuthenticationManager manager = mock(AuthenticationManager.class);
        user.setTokensValidAfter(Instant.now().plusMillis(1));
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        ReflectionTestUtils.setField(controller, "authenticationManager", manager);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setEmail(user.getEmail());
        dto.setPassword("Example1");
        String token = ((com.spotmeet.backend.dto.AuthResponseDTO) controller.login(dto).getBody()).getToken();
        assertTrue(jwt.isTokenValid(token, user.getEmail(), user.getTokensValidAfter()));
    }
    @Test void emailChangeKeepsTheOriginalSessionIssueDate() {
        UserController controller = new UserController();
        UserService service = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        Instant original = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(2);
        String previous = jwt.generateToken(user.getEmail(), "USER", original);
        User updated = new User();
        updated.setEmail("new@example.com");
        when(service.completeEmailChange(anyString(), any())).thenReturn(updated);
        var result = controller.completeEmailChange(new CompleteEmailChangeDTO("012345"),
                new UsernamePasswordAuthenticationToken(user.getEmail(), null), "Bearer " + previous);
        String token = (String) ((Map<?, ?>) result.getBody()).get("token");
        assertEquals(original, jwt.extractIssuedAt(token));
        assertFalse(jwt.isTokenValid(token, updated.getEmail(), original.plusMillis(1)));
    }
    @Test void malformedEmailChangeSessionIs401BeforeOtpConsumption() {
        UserController controller = new UserController();
        UserService service = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        var result = controller.completeEmailChange(new CompleteEmailChangeDTO("012345"),
                new UsernamePasswordAuthenticationToken(user.getEmail(), null), "Bearer invalid-test-jwt");
        assertEquals(401, result.getStatusCode().value());
        verifyNoInteractions(service);
    }
}
