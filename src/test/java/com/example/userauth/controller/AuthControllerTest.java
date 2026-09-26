package com.example.userauth.controller;

import com.example.userauth.dto.RegisterRequest;
import com.example.userauth.dto.RegisterResponse;
import com.example.userauth.exception.UserAlreadyExistsException;
import com.example.userauth.security.JwtAuthenticationFilter;
import com.example.userauth.service.AuthenticationService;
import com.example.userauth.service.PasswordChangeService;
import com.example.userauth.service.PasswordResetService;
import com.example.userauth.service.RegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@WithMockUser
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private PasswordChangeService passwordChangeService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void registerReturnsCreatedForValidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Abhimanyu", "Kumar", "abhimanyu@example.com", "Password@123", "9876543210");
        UUID userId = UUID.randomUUID();
        when(registrationService.register(any())).thenReturn(
                new RegisterResponse("User registered successfully", userId));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void registerReturnsBadRequestForInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Abhimanyu", "Kumar", "not-an-email", "Password@123", "9876543210");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturnsConflictForDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Abhimanyu", "Kumar", "abhimanyu@example.com", "Password@123", "9876543210");
        when(registrationService.register(any()))
                .thenThrow(new UserAlreadyExistsException("A user with this email already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
