package com.example.userauth.service;

import com.example.userauth.dto.LoginRequest;
import com.example.userauth.dto.LoginResponse;
import com.example.userauth.entity.User;
import com.example.userauth.entity.UserStatus;
import com.example.userauth.exception.AccountLockedException;
import com.example.userauth.exception.InvalidCredentialsException;
import com.example.userauth.mapper.UserMapper;
import com.example.userauth.repository.UserRepository;
import com.example.userauth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (loginAttemptService.isBlocked(normalizedEmail)) {
            throw new AccountLockedException("Account temporarily locked due to repeated failed login attempts. Try again later.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailedAttempt(normalizedEmail);
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailedAttempt(normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountLockedException("Account is not active");
        }

        loginAttemptService.reset(normalizedEmail);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name());

        log.info("User {} logged in successfully", user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .user(userMapper.toUserResponse(user))
                .build();
    }
}
