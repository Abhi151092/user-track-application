package com.example.userauth.service;

import com.example.userauth.entity.PasswordResetToken;
import com.example.userauth.entity.User;
import com.example.userauth.exception.InvalidTokenException;
import com.example.userauth.exception.TokenExpiredException;
import com.example.userauth.repository.PasswordResetTokenRepository;
import com.example.userauth.repository.UserRepository;
import com.example.userauth.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMinutes", 15L);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("abhimanyu@example.com")
                .password("old-hashed-password")
                .build();
    }

    @Test
    void forgotPasswordCreatesTokenForExistingUser() {
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.of(user));

        passwordResetService.forgotPassword("Abhimanyu@Example.com");

        verify(tokenRepository).invalidateActiveTokensForUser(user.getId());
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void forgotPasswordDoesNothingForUnknownEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        passwordResetService.forgotPassword("unknown@example.com");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordSucceedsForValidToken() {
        String rawToken = "raw-token";
        String tokenHash = TokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new-hashed-password");

        passwordResetService.resetPassword(rawToken, "NewPassword@123");

        verify(userRepository).save(user);
        verify(tokenRepository).save(resetToken);
        verify(tokenRepository).invalidateActiveTokensForUser(user.getId());
    }

    @Test
    void resetPasswordFailsForExpiredToken() {
        String rawToken = "raw-token";
        String tokenHash = TokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(600))
                .used(false)
                .build();

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "NewPassword@123"))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void resetPasswordFailsForAlreadyUsedToken() {
        String rawToken = "raw-token";
        String tokenHash = TokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(true)
                .build();

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "NewPassword@123"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resetPasswordFailsForUnknownToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("bad-token", "NewPassword@123"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
