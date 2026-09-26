package com.example.userauth.service;

import com.example.userauth.dto.RegisterRequest;
import com.example.userauth.dto.RegisterResponse;
import com.example.userauth.entity.User;
import com.example.userauth.exception.UserAlreadyExistsException;
import com.example.userauth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest(
                "Abhimanyu",
                "Kumar",
                "Abhimanyu@Example.com",
                "Password@123",
                "9876543210"
        );
    }

    @Test
    void registersUserSuccessfully() {
        when(userRepository.existsByEmail("abhimanyu@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        RegisterResponse response = registrationService.register(request);

        assertThat(response.getMessage()).isEqualTo("User registered successfully");
        assertThat(response.getUserId()).isNotNull();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("abhimanyu@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void throwsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("abhimanyu@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void normalizesEmailToLowercaseBeforeChecking() {
        when(userRepository.existsByEmail("abhimanyu@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.register(request);

        verify(userRepository).existsByEmail("abhimanyu@example.com");
    }
}
