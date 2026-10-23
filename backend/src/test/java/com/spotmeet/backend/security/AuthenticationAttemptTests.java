package com.spotmeet.backend.security;

import com.spotmeet.backend.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.spotmeet.backend.security.AuthenticationAttempts.Purpose.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationAttemptTests {
    private final AtomicLong time = new AtomicLong();
    private AuthenticationAttempts attempts;
    private static final long WINDOW = Duration.ofMinutes(15).toMillis();

    @BeforeEach void setup() { attempts = new AuthenticationAttempts(time::get, 100); }

    private void failures(int count) {
        for (int i = 0; i < count; i++) assertThrows(BadCredentialsException.class,
                () -> attempts.verify(LOGIN, "user@example.com", () -> { throw new BadCredentialsException("Invalid"); }));
    }

    @Test void tenthFailureBlocksEvenCorrectCredentialsUntilTheExactDeadline() {
        failures(9);
        TooManyAttemptsException tenth = assertThrows(TooManyAttemptsException.class,
                () -> attempts.verify(LOGIN, "user@example.com", () -> { throw new BadCredentialsException("Invalid"); }));
        assertEquals(900, tenth.getRetryAfterSeconds());
        time.set(WINDOW - 1);
        assertThrows(TooManyAttemptsException.class,
                () -> attempts.verify(LOGIN, "user@example.com", () -> fail("Must not validate while blocked")));
        time.set(WINDOW);
        assertEquals("ok", attempts.verify(LOGIN, "user@example.com", () -> "ok"));
    }

    @Test void blockedRequestsDoNotExtendThePause() {
        failures(9);
        assertThrows(TooManyAttemptsException.class, () -> attempts.verify(LOGIN, "user@example.com", () -> { throw new BadCredentialsException("Invalid"); }));
        for (int i = 1; i < 15; i++) {
            time.set(Duration.ofMinutes(i).toMillis());
            TooManyAttemptsException blocked = assertThrows(TooManyAttemptsException.class,
                    () -> attempts.verify(LOGIN, "user@example.com", () -> "ok"));
            assertEquals(900 - i * 60, blocked.getRetryAfterSeconds());
        }
        time.set(WINDOW);
        assertEquals("ok", attempts.verify(LOGIN, "user@example.com", () -> "ok"));
    }

    @Test void expiredFailureWindowsAndSuccessfulVerificationResetTheFailureCount() {
        failures(9); time.set(WINDOW); failures(9);
        assertEquals("ok", attempts.verify(LOGIN, "user@example.com", () -> "ok"));
        failures(9);
    }

    @Test void normalizationIgnoresCaseAndSurroundingSpacesIndependentlyOfServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            for (int i = 0; i < 9; i++) assertThrows(IllegalArgumentException.class,
                    () -> attempts.verify(RECOVERY, "  USER@EXAMPLE.COM  ", () -> { throw new IllegalArgumentException("Invalid code"); }));
            assertThrows(TooManyAttemptsException.class,
                    () -> attempts.verify(RECOVERY, "user@example.com", () -> { throw new IllegalArgumentException("Invalid code"); }));
        } finally { Locale.setDefault(previous); }
    }

    @Test void accountsAndPurposesHaveIndependentBudgets() {
        failures(9);
        assertEquals("ok", attempts.verify(LOGIN, "another@example.com", () -> "ok"));
        assertEquals("ok", attempts.verify(RECOVERY, "user@example.com", () -> "ok"));
        assertEquals("ok", attempts.verify(CURRENT_PASSWORD, "user:1", () -> "ok"));
        assertThrows(TooManyAttemptsException.class,
                () -> attempts.verify(LOGIN, "user@example.com", () -> { throw new BadCredentialsException("Invalid"); }));
    }

    @Test void infrastructureFailuresDoNotConsumeTheCredentialBudgetOrLeakReservations() {
        for (int i = 0; i < 20; i++) assertThrows(IllegalStateException.class,
                () -> attempts.verify(LOGIN, "user@example.com", () -> { throw new IllegalStateException("Unavailable"); }));
        failures(9);
        assertEquals("ok", attempts.verify(LOGIN, "user@example.com", () -> "ok"));
    }

    @Test void concurrentRequestsCannotReserveMoreThanTheRemainingBudget() throws Exception {
        CountDownLatch started = new CountDownLatch(10), release = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(20)) {
            ArrayList<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) calls.add(workers.submit(() -> {
                assertThrows(RuntimeException.class, () -> attempts.verify(LOGIN, "user@example.com", () -> {
                    checks.incrementAndGet(); started.countDown();
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException exception) { throw new AssertionError(exception); }
                    throw new BadCredentialsException("Invalid");
                }));
            }));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                for (int i = 0; i < 20; i++) assertThrows(TooManyAttemptsException.class,
                        () -> attempts.verify(LOGIN, "USER@example.com", () -> fail("Must not bypass reservations")));
            } finally { release.countDown(); }
            for (Future<?> call : calls) call.get(5, TimeUnit.SECONDS);
            assertEquals(10, checks.get());
            assertThrows(TooManyAttemptsException.class, () -> attempts.verify(LOGIN, "user@example.com", () -> "ok"));
        }
    }

    @Test void capacityNeverEvictsAnActiveBlockAndExpiredEntriesAreReclaimed() {
        attempts = new AuthenticationAttempts(time::get, 1);
        failures(9);
        assertThrows(TooManyAttemptsException.class,
                () -> attempts.verify(LOGIN, "user@example.com", () -> { throw new BadCredentialsException("Invalid"); }));
        assertThrows(TooManyAttemptsException.class, () -> attempts.verify(LOGIN, "another@example.com", () -> "ok"));
        assertThrows(TooManyAttemptsException.class, () -> attempts.verify(LOGIN, "user@example.com", () -> "ok"));
        time.set(WINDOW);
        assertEquals("ok", attempts.verify(LOGIN, "another@example.com", () -> "ok"));
    }
}
