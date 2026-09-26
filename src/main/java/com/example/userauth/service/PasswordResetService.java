package com.example.userauth.service;

import com.example.userauth.entity.PasswordResetToken;
import com.example.userauth.entity.User;
import com.example.userauth.exception.InvalidTokenException;
import com.example.userauth.exception.TokenExpiredException;
import com.example.userauth.repository.PasswordResetTokenRepository;
import com.example.userauth.repository.UserRepository;
import com.example.userauth.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.password-reset.token-expiration-minutes}")
    private long tokenExpirationMinutes;

    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        // Always behave the same way regardless of whether the account exists,
        // to avoid leaking which emails are registered.
        if (userOptional.isEmpty()) {
            log.info("Password reset requested for an email with no matching account");
            return;
        }

        User user = userOptional.get();

        tokenRepository.invalidateActiveTokensForUser(user.getId());

        String rawToken = TokenUtil.generateSecureToken();
        String tokenHash = TokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(tokenExpirationMinutes * 60))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        // DEVELOPMENT ONLY: in production this raw token must be emailed to the
        // user via a transactional email provider, never logged.
        log.info("[DEV ONLY] Password reset link for user {}: /reset-password?token={}", user.getId(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = TokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or unknown reset token"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("This reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException("This reset token has expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Invalid or unknown reset token"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        tokenRepository.invalidateActiveTokensForUser(user.getId());

        log.info("Password reset completed for user {}", user.getId());
    }
}
