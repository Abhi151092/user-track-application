package com.example.userauth.service;

import com.example.userauth.entity.User;
import com.example.userauth.exception.PasswordMismatchException;
import com.example.userauth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("abhimanyu@example.com")
                .password("old-hashed-password")
                .build();
    }

    @Test
    void changesPasswordSuccessfully() {
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword@123", "old-hashed-password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new-hashed-password");

        passwordChangeService.changePassword("abhimanyu@example.com", "OldPassword@123", "NewPassword@123");

        verify(userRepository).save(user);
    }

    @Test
    void throwsWhenCurrentPasswordIsWrong() {
        when(userRepository.findByEmail("abhimanyu@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword@123", "old-hashed-password")).thenReturn(false);

        assertThatThrownBy(() ->
                passwordChangeService.changePassword("abhimanyu@example.com", "WrongPassword@123", "NewPassword@123"))
                .isInstanceOf(PasswordMismatchException.class);

        verify(userRepository, never()).save(any());
    }
}
