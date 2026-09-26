package com.example.userauth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight, in-memory brute-force protection.
 * Note: this is per-instance state and will not survive restarts or work
 * across multiple service instances. Replace with a shared store (e.g. Redis)
 * before scaling horizontally.
 */
@Slf4j
@Service
public class LoginAttemptService {

    private final int maxFailedAttempts;
    private final long lockoutDurationMillis;

    private final ConcurrentHashMap<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.login.max-failed-attempts}") int maxFailedAttempts,
            @Value("${app.security.login.lockout-duration-minutes}") long lockoutDurationMinutes
    ) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationMillis = lockoutDurationMinutes * 60 * 1000;
    }

    public boolean isBlocked(String email) {
        Instant until = lockedUntil.get(normalize(email));
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            reset(email);
            return false;
        }
        return true;
    }

    public void recordFailedAttempt(String email) {
        String key = normalize(email);
        int attempts = failedAttempts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (attempts >= maxFailedAttempts) {
            lockedUntil.put(key, Instant.now().plusMillis(lockoutDurationMillis));
            log.warn("Account temporarily locked due to repeated failed login attempts");
        }
    }

    public void reset(String email) {
        String key = normalize(email);
        failedAttempts.remove(key);
        lockedUntil.remove(key);
    }

    private String normalize(String email) {
        return email == null ? "" : email.toLowerCase();
    }
}
