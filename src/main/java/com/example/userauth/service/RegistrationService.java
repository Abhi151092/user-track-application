package com.example.userauth.service;

import com.example.userauth.dto.RegisterRequest;
import com.example.userauth.dto.RegisterResponse;
import com.example.userauth.entity.User;
import com.example.userauth.entity.UserRole;
import com.example.userauth.entity.UserStatus;
import com.example.userauth.exception.UserAlreadyExistsException;
import com.example.userauth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("A user with this email already exists");
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered with id {}", saved.getId());

        return new RegisterResponse("User registered successfully", saved.getId());
    }
}
