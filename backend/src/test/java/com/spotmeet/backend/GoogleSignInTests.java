package com.spotmeet.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.GoogleKeysClient;
import com.spotmeet.backend.service.GoogleSignInService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real service and controller; Google's keys and the persistence are replaced.
class GoogleSignInTests {
    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";
    private static final String KID = "key-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Map<String, User> users = new HashMap<>();
    private final List<AuditLog> audit = new ArrayList<>();
    private KeyPair googleKeys;
    private GoogleKeysClient keysClient;
    private GoogleSignInService service;

    @BeforeEach void setUp() throws Exception {
        users.clear();
        audit.clear();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKeys = generator.generateKeyPair();

        keysClient = mock(GoogleKeysClient.class);
        when(keysClient.fetch()).thenAnswer(i -> jwks(KID, (RSAPublicKey) googleKeys.getPublic()));
        UserRepository userRepo = mock(UserRepository.class);
        AuditLogRepository auditRepo = mock(AuditLogRepository.class);
        when(userRepo.findByEmailForUpdate(anyString())).thenAnswer(i -> Optional.ofNullable(users.get(i.<String>getArgument(0))));
        when(userRepo.save(any(User.class))).thenAnswer(i -> {
            User user = i.getArgument(0);
            if (user.getId() == null) user.setId((long) users.size() + 1);
            users.put(user.getEmail(), user);
            return user;
        });
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(i -> { audit.add(i.getArgument(0)); return i.getArgument(0); });

        service = new GoogleSignInService();
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(service, "keysClient", keysClient);
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "auditLogRepo", auditRepo);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "mapper", mapper);
    }

    private JsonNode jwks(String kid, RSAPublicKey key) {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        ObjectNode jwk = mapper.createObjectNode();
        jwk.put("kid", kid);
        jwk.put("kty", "RSA");
        jwk.put("n", url.encodeToString(key.getModulus().toByteArray()[0] == 0
                ? java.util.Arrays.copyOfRange(key.getModulus().toByteArray(), 1, key.getModulus().toByteArray().length)
                : key.getModulus().toByteArray()));
        jwk.put("e", url.encodeToString(key.getPublicExponent().toByteArray()));
        ArrayNode keys = mapper.createArrayNode().add(jwk);
        return mapper.createObjectNode().set("keys", keys);
    }

    private String token(String audience, String email, Object emailVerified, String name, KeyPair signer, String kid, long secondsToLive) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .header().keyId(kid).and()
                .issuer("https://accounts.google.com")
                .audience().add(audience).and()
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(secondsToLive)))
                .claim("email", email)
                .claim("email_verified", emailVerified);
        if (name != null) builder.claim("name", name);
        return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String valid(String email, String name) {
        return token(CLIENT_ID, email, true, name, googleKeys, KID, 600);
    }

    private KeyPair otherKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Test void theButtonIsOffUntilTheServerHasTheClientId() {
        assertTrue(service.isEnabled());
        ReflectionTestUtils.setField(service, "clientId", "");
        assertFalse(service.isEnabled());
        assertThrows(IllegalStateException.class, () -> service.signIn(valid("a@gmail.com", "Ana Lima")));
    }

    @Test void aNewPersonGetsAVerifiedAccountWithTheGoogleName() {
        User created = service.signIn(valid("Nova.Pessoa@Gmail.com", "Nova  Pessoa"));

        assertSame(created, users.get("nova.pessoa@gmail.com"));
        assertEquals("Nova Pessoa", created.getName());
        assertEquals("USER", created.getRole());
        assertTrue(created.isEmailVerified());
        assertNotNull(created.getTokensValidAfter());
        assertEquals("USER_REGISTERED_GOOGLE", audit.get(0).getAction());
    }

    @Test void aPersonWhoAlreadyHasAVerifiedAccountEntersThatAccountUntouched() {
        User existing = new User();
        existing.setId(7L); existing.setName("Maria"); existing.setEmail("maria@gmail.com");
        existing.setPassword(encoder.encode("Senha@123")); existing.setEmailVerified(true);
        users.put(existing.getEmail(), existing);
        String oldHash = existing.getPassword();

        assertSame(existing, service.signIn(valid("maria@gmail.com", "Maria Google")));
        assertEquals(1, users.size());
        assertEquals("Maria", existing.getName());
        assertEquals(oldHash, existing.getPassword());
        assertTrue(audit.isEmpty());
    }

    @Test void anUnconfirmedAccountIsConfirmedAndItsPasswordStopsWorking() {
        User pending = new User();
        pending.setId(9L); pending.setName("Joao"); pending.setEmail("joao@gmail.com");
        pending.setPassword(encoder.encode("SenhaDeOutro@1")); pending.setEmailVerified(false);
        pending.setVerificationToken("hash");
        users.put(pending.getEmail(), pending);

        service.signIn(valid("joao@gmail.com", "Joao"));

        assertTrue(pending.isEmailVerified());
        assertNull(pending.getVerificationToken());
        assertFalse(encoder.matches("SenhaDeOutro@1", pending.getPassword()));
        assertNotNull(pending.getTokensValidAfter());
    }

    @Test void aMissingOrShortGoogleNameFallsBackToTheStartOfTheAddress() {
        service.signIn(valid("ana.lima@gmail.com", null));
        service.signIn(valid("b@gmail.com", "B"));
        assertEquals("ana.lima", users.get("ana.lima@gmail.com").getName());
        assertEquals("b", users.get("b@gmail.com").getName());
    }

    @Test void aTokenThatGoogleDidNotSignIsRefused() throws Exception {
        String forged = token(CLIENT_ID, "a@gmail.com", true, "A B", otherKeys(), KID, 600);
        assertThrows(IllegalArgumentException.class, () -> service.signIn(forged));
        assertTrue(users.isEmpty());
    }

    @Test void aTokenForAnotherAppAnUnverifiedAddressOrAnExpiredTokenIsRefused() {
        String[] bad = {
                token("other-client", "a@gmail.com", true, "A B", googleKeys, KID, 600),
                token(CLIENT_ID, "a@gmail.com", false, "A B", googleKeys, KID, 600),
                token(CLIENT_ID, null, true, "A B", googleKeys, KID, 600),
                token(CLIENT_ID, "a@gmail.com", true, "A B", googleKeys, KID, -60)
        };
        for (String token : bad) {
            assertThrows(IllegalArgumentException.class, () -> service.signIn(token));
        }
        assertTrue(users.isEmpty());
    }

    @Test void anUnknownKeyIdOrGarbageIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> service.signIn(token(CLIENT_ID, "a@gmail.com", true, "A B", googleKeys, "unknown", 600)));
        assertThrows(IllegalArgumentException.class, () -> service.signIn("not-a-token"));
        assertThrows(IllegalArgumentException.class, () -> service.signIn("a.b.c"));
        assertTrue(users.isEmpty());
    }

    @Test void googleKeysAreFetchedOnceAndReused() throws Exception {
        service.signIn(valid("a1@gmail.com", "Ana Um"));
        service.signIn(valid("a2@gmail.com", "Ana Dois"));
        verify(keysClient, times(1)).fetch();
    }

    @Test void whenGoogleKeysCannotBeDownloadedTheSignInIsRefused() throws Exception {
        when(keysClient.fetch()).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalArgumentException.class, () -> service.signIn(valid("a@gmail.com", "Ana Lima")));
    }

    private MockMvc routes() {
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(anyString(), anyString(), any(Instant.class))).thenReturn("session-token");
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "googleSignIn", service);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void theStatusRouteTellsTheAppWhichClientToUse() throws Exception {
        MockMvc mvc = routes();
        mvc.perform(get("/api/auth/google/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.clientId").value(CLIENT_ID));
        ReflectionTestUtils.setField(service, "clientId", "");
        mvc.perform(get("/api/auth/google/status")).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.clientId").doesNotExist());
    }

    @Test void theSignInRouteReturnsASessionLikeTheNormalLogin() throws Exception {
        String body = mapper.writeValueAsString(Map.of("idToken", valid("rota@gmail.com", "Rota Teste")));
        routes().perform(post("/api/auth/google").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("session-token"))
                .andExpect(jsonPath("$.email").value("rota@gmail.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test void theSignInRouteRefusesBadTokensAndAServerWithoutGoogle() throws Exception {
        MockMvc mvc = routes();
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"a.b.c\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        ReflectionTestUtils.setField(service, "clientId", "");
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"a.b.c\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
