package com.spotmeet.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Sign-in with Google. The app gets an ID token from the Google account chooser on the device
 * and sends it here; the server checks Google's signature and the token claims, then finds or
 * creates the account. No secret and no public address are needed.
 */
@Service
public class GoogleSignInService {

    private static final Duration KEY_LIFETIME = Duration.ofHours(1);
    private static final Duration KEY_RETRY = Duration.ofMinutes(1);
    private static final String FAILURE = "Não foi possível entrar com o Google. Tente novamente.";

    @Value("${spotmeet.google.client-id:}")
    private String clientId;

    @Autowired
    private GoogleKeysClient keysClient;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper mapper;

    private final SecureRandom random = new SecureRandom();
    private Map<String, PublicKey> keys = Map.of();
    private Instant keysLoadedAt = Instant.EPOCH;

    public boolean isEnabled() {
        return !clientId.isBlank();
    }

    public String getClientId() {
        return clientId;
    }

    /** Verifies the token and returns the matching account, creating it on the first sign-in. */
    @Transactional
    public User signIn(String idToken) {
        if (!isEnabled()) {
            throw new IllegalStateException("O login com Google não está configurado neste servidor.");
        }
        Claims claims = verify(idToken);
        String email = String.valueOf(claims.get("email")).trim().toLowerCase(Locale.ROOT);
        String name = claims.get("name") == null ? "" : String.valueOf(claims.get("name"));
        return findOrCreateUser(email, name);
    }

    private Claims verify(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            String kid = mapper.readTree(Base64.getUrlDecoder().decode(parts[0])).path("kid").asText("");
            PublicKey key = keyFor(kid);
            if (key == null) throw new IllegalArgumentException(FAILURE);

            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(idToken).getPayload();
            String issuer = claims.getIssuer() == null ? "" : claims.getIssuer();
            boolean issuerOk = issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com");
            boolean audienceOk = claims.getAudience() != null && claims.getAudience().contains(clientId);
            boolean emailOk = claims.get("email") != null && !String.valueOf(claims.get("email")).isBlank()
                    && "true".equalsIgnoreCase(String.valueOf(claims.get("email_verified")));
            if (!issuerOk || !audienceOk || !emailOk) throw new IllegalArgumentException(FAILURE);
            return claims;
        } catch (Exception e) {
            throw new IllegalArgumentException(FAILURE);
        }
    }

    private synchronized PublicKey keyFor(String kid) throws Exception {
        Instant now = Instant.now();
        boolean stale = keysLoadedAt.plus(KEY_LIFETIME).isBefore(now);
        boolean unknownAndRetryable = !keys.containsKey(kid) && keysLoadedAt.plus(KEY_RETRY).isBefore(now);
        if (stale || unknownAndRetryable) {
            Map<String, PublicKey> loaded = new HashMap<>();
            KeyFactory factory = KeyFactory.getInstance("RSA");
            JsonNode set = keysClient.fetch();
            for (JsonNode jwk : set.path("keys")) {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
                loaded.put(jwk.path("kid").asText(), factory.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            }
            keys = loaded;
            keysLoadedAt = now;
        }
        return keys.get(kid);
    }

    private User findOrCreateUser(String email, String googleName) {
        Optional<User> existing = userRepo.findByEmailForUpdate(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isEmailVerified()) {
                // Google proves the owner of the address, so a password set by someone else must stop working.
                user.setEmailVerified(true);
                user.setPassword(passwordEncoder.encode(randomSecret()));
                user.setVerificationToken(null);
                user.setVerificationTokenExpiresAt(null);
                user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.MILLIS));
                User saved = userRepo.save(user);
                auditLogRepo.save(new AuditLog(saved, "USER_REGISTERED_GOOGLE", "Conta pendente confirmada pelo Google: " + saved.getEmail()));
                return saved;
            }
            return user;
        }

        User user = new User();
        user.setName(displayName(googleName, email));
        user.setEmail(email);
        // Accounts created here have no password the person knows; recovery by e-mail can set one.
        user.setPassword(passwordEncoder.encode(randomSecret()));
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        User saved = userRepo.save(user);
        auditLogRepo.save(new AuditLog(saved, "USER_REGISTERED_GOOGLE", "Cadastro pelo Google: " + saved.getEmail()));
        return saved;
    }

    private String displayName(String googleName, String email) {
        String name = googleName.replaceAll("\\s+", " ").trim();
        if (name.length() < 2) {
            name = email.substring(0, email.indexOf('@'));
        }
        return name.length() > 100 ? name.substring(0, 100).trim() : name;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
