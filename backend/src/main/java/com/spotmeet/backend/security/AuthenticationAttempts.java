package com.spotmeet.backend.security;

import com.spotmeet.backend.exception.TooManyAttemptsException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
public class AuthenticationAttempts {
    public enum Purpose { LOGIN, CURRENT_PASSWORD, EMAIL_VERIFICATION, RECOVERY, CURRENT_EMAIL, NEW_EMAIL }

    private static final int MAX_FAILURES = 10;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long CLEANUP_MILLIS = Duration.ofMinutes(1).toMillis();
    private final Map<Key, Window> windows = new HashMap<>();
    private final LongSupplier ticker;
    private final int capacity;
    private long nextCleanup;

    public AuthenticationAttempts() {
        this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), 10_000);
    }

    AuthenticationAttempts(LongSupplier ticker, int capacity) {
        this.ticker = ticker;
        this.capacity = capacity;
    }

    // The callback validates credentials only; business mutations stay outside it.
    public <T> T verify(Purpose purpose, String identity, Supplier<T> verification) {
        Key key = new Key(purpose, identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT));
        Window window = begin(key);
        try {
            T result = verification.get();
            succeeded(window);
            return result;
        } catch (BadCredentialsException | DisabledException | IllegalArgumentException exception) {
            long retryAfter = failed(window);
            if (retryAfter > 0) throw new TooManyAttemptsException(retryAfter);
            throw exception;
        } finally {
            release(window);
        }
    }

    private synchronized Window begin(Key key) {
        long now = ticker.getAsLong();
        if (now >= nextCleanup || windows.size() >= capacity) {
            windows.values().removeIf(window -> window.pending == 0
                    && now - window.lastUsed >= WINDOW_MILLIS && now >= window.blockedUntil);
            nextCleanup = now + CLEANUP_MILLIS;
        }
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= capacity) throw new TooManyAttemptsException(60);
            window = new Window(now);
            windows.put(key, window);
        }
        resetExpired(window, now);
        if (now < window.blockedUntil) throw new TooManyAttemptsException(seconds(window.blockedUntil - now));
        // Reserve the remaining attempts before expensive concurrent verification.
        if (window.failures + window.pending >= MAX_FAILURES) throw new TooManyAttemptsException(1);
        window.pending++;
        window.lastUsed = now;
        return window;
    }

    private synchronized void succeeded(Window window) {
        window.failures = 0;
        window.blockedUntil = Long.MIN_VALUE;
        window.lastUsed = ticker.getAsLong();
    }

    private synchronized long failed(Window window) {
        long now = ticker.getAsLong();
        resetExpired(window, now);
        if (window.failures == 0) window.startedAt = now;
        window.failures++;
        window.lastUsed = now;
        if (window.failures == MAX_FAILURES) window.blockedUntil = now + WINDOW_MILLIS;
        return now < window.blockedUntil ? seconds(window.blockedUntil - now) : 0;
    }

    private synchronized void release(Window window) {
        window.pending--;
    }

    private static void resetExpired(Window window, long now) {
        if (window.failures > 0 && now - window.startedAt >= WINDOW_MILLIS && now >= window.blockedUntil) {
            window.failures = 0;
            window.blockedUntil = Long.MIN_VALUE;
        }
    }

    private static long seconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    private record Key(Purpose purpose, String identity) {}

    private static final class Window {
        int failures;
        int pending;
        long startedAt;
        long lastUsed;
        long blockedUntil = Long.MIN_VALUE;

        Window(long now) { lastUsed = now; }
    }
}
