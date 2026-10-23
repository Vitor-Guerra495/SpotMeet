package com.spotmeet.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.config.SecurityConfig;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.dto.CodeDeliveryResultDTO;
import com.spotmeet.backend.dto.RegisterRequestDTO;
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
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// Fixes from the security review of 18/10/2026.
class SecurityReviewTests {
    private static final String EMAIL = "pending@example.com";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecurityConfig config = new SecurityConfig();
    private User user;
    private AuthenticationProvider provider;
    private MockMvc mvc;

    @RestController
    static class Probe {
        @GetMapping("/probe/{id}") String byId(@PathVariable Long id) { return "ok"; }
        @PostMapping("/probe/flag") String flag(@RequestParam boolean accept) { return "ok"; }
        @GetMapping("/probe/fail") String fail() { throw new RuntimeException("java.lang.NullPointerException detalhe interno"); }
    }

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(5L);
        user.setName("Pending User");
        user.setEmail(EMAIL);
        user.setRole("USER");
        user.setPassword(config.passwordEncoder().encode("Owner2026a"));
        user.setEmailVerified(false);
        UserDetailsService details = email -> {
            if (EMAIL.equals(email)) return user;
            throw new UsernameNotFoundException("not found");
        };
        ReflectionTestUtils.setField(config, "userDetailsService", details);
        provider = config.authenticationProvider();

        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(jwt, "expirationMs", 3_600_000L);
        AuthController auth = new AuthController();
        ReflectionTestUtils.setField(auth, "attempts", new AuthenticationAttempts());
        ReflectionTestUtils.setField(auth, "authenticationManager", new ProviderManager(provider));
        ReflectionTestUtils.setField(auth, "jwtUtil", jwt);
        mvc = MockMvcBuilders.standaloneSetup(auth, new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private UsernamePasswordAuthenticationToken credentials(String email, String password) {
        return new UsernamePasswordAuthenticationToken(email, password);
    }

    private MvcResult login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(JSON.writeValueAsBytes(Map.of("email", EMAIL, "password", password)))).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    void pendingAccountWithWrongPasswordIsReportedAsBadCredentials() {
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(credentials(EMAIL, "Intruder2026b")));
    }

    @Test
    void pendingAccountIsReportedOnlyWithTheCorrectPassword() {
        assertThrows(DisabledException.class, () -> provider.authenticate(credentials(EMAIL, "Owner2026a")));
    }

    @Test
    void verifiedAccountAndUnknownAccountKeepTheirResults() {
        user.setEmailVerified(true);
        assertSame(user, provider.authenticate(credentials(EMAIL, "Owner2026a")).getPrincipal());
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(credentials("other@example.com", "Owner2026a")));
    }

    @Test
    void loginRevealsThePendingVerificationOnlyToTheOwner() throws Exception {
        MvcResult wrong = login("Intruder2026b");
        assertEquals(401, wrong.getResponse().getStatus());
        assertEquals("BAD_CREDENTIALS", body(wrong).get("error").asText());
        assertFalse(wrong.getResponse().getContentAsString().contains(EMAIL));

        MvcResult right = login("Owner2026a");
        assertEquals(403, right.getResponse().getStatus());
        assertEquals("EMAIL_NOT_VERIFIED", body(right).get("error").asText());
    }

    @Test
    void clientErrorsReturnTheirStatusWithoutFrameworkDetails() throws Exception {
        MvcResult unreadable = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\": \"a@b.com\", \"password\": ")).andReturn();
        MvcResult method = mvc.perform(delete("/api/auth/login")).andReturn();
        MvcResult type = mvc.perform(get("/probe/{id}", "1 OR 1=1")).andReturn();
        MvcResult missing = mvc.perform(post("/probe/flag")).andReturn();
        assertEquals(400, unreadable.getResponse().getStatus());
        assertEquals(405, method.getResponse().getStatus());
        assertEquals(400, type.getResponse().getStatus());
        assertEquals(400, missing.getResponse().getStatus());
        for (MvcResult result : new MvcResult[]{unreadable, method, type, missing}) {
            String text = result.getResponse().getContentAsString();
            assertFalse(text.contains("java.lang"), text);
            assertFalse(text.contains("JSON parse error"), text);
            assertFalse(text.contains("Failed to convert"), text);
            assertFalse(text.contains("not supported"), text);
            assertFalse(text.contains("Required request parameter"), text);
        }
    }

    @Test
    void unknownRouteIsNotFoundAndUnexpectedErrorsHideTheirMessage() throws Exception {
        var notFound = new GlobalExceptionHandler().handleNotFound(new NoResourceFoundException(HttpMethod.GET, "api/nao-existe"));
        assertEquals(404, notFound.getStatusCode().value());
        assertFalse(notFound.getBody().toString().contains("No static resource"));

        MvcResult failure = mvc.perform(get("/probe/fail")).andReturn();
        assertEquals(500, failure.getResponse().getStatus());
        assertEquals("Erro interno no servidor.", body(failure).get("message").asText());
        assertFalse(failure.getResponse().getContentAsString().contains("detalhe interno"));
    }

    @Test
    void newAccountRejectsTokensIssuedToAPreviousOwnerOfTheAddress() {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(jwt, "expirationMs", 3_600_000L);
        String previousOwnerToken = jwt.generateToken("freed@example.com", "USER", Instant.now().minusSeconds(60));

        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailForUpdate("freed@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "emailService", mock(EmailService.class));
        ReflectionTestUtils.setField(service, "auditLogRepo", mock(AuditLogRepository.class));
        RegisterRequestDTO dto = new RegisterRequestDTO();
        dto.setName("New Owner");
        dto.setEmail("freed@example.com");
        dto.setPassword("NewOwner2026c");

        CodeDeliveryResultDTO result = service.registerUser(dto);
        Instant cutoff = result.getUser().getTokensValidAfter();
        assertNotNull(cutoff);
        assertFalse(jwt.isTokenValid(previousOwnerToken, "freed@example.com", cutoff));
        assertTrue(jwt.isTokenValid(jwt.generateToken("freed@example.com", "USER", cutoff.plusMillis(1)), "freed@example.com", cutoff));
    }
}
