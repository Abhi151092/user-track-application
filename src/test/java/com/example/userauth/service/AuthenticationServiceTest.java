package com.example.userauth.service;

import com.example.userauth.dto.LoginRequest;
import com.example.userauth.dto.LoginResponse;
import com.example.userauth.dto.UserResponse;
import com.example.userauth.entity.User;
import com.example.userauth.entity.UserRole;
import com.example.userauth.entity.UserStatus;
import com.example.userauth.exception.AccountLockedException;
import com.example.userauth.exception.InvalidCredentialsException;
import com.example.userauth.mapper.UserMapper;
import com.example.userauth.repository.UserRepository;
import com.example.userauth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User activeUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("Abhimanyu")
                .lastName("Kumar")
                .email("abhimanyu@example.com")
                .password("hashed-password")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();

        loginRequest = new LoginRequest("abhimanyu@example.com", "Password@123");
    }

    @Test
    void loginSucceedsForValidCredentials() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Password@123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userMapper.toUserResponse(activeUser)).thenReturn(
                UserResponse.builder().id(activeUser.getId()).email(activeUser.getEmail()).build());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authenticationService.login(loginRequest);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        verify(loginAttemptService).reset("abhimanyu@example.com");
    }

    @Test
    void loginFailsForWrongPassword() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Password@123", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailedAttempt("abhimanyu@example.com");
    }

    @Test
    void loginFailsForUnknownUser() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailedAttempt("abhimanyu@example.com");
    }

    @Test
    void loginFailsForInactiveUser() {
        activeUser.setStatus(UserStatus.INACTIVE);
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Password@123", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void loginBlockedAfterTooManyFailedAttempts() {
        when(loginAttemptService.isBlocked("abhimanyu@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }
}
