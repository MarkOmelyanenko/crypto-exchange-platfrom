package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.config.JacksonConfig;
import com.cryptoexchange.backend.config.JwtAuthenticationFilter;
import com.cryptoexchange.backend.config.JwtService;
import com.cryptoexchange.backend.config.RateLimitProperties;
import com.cryptoexchange.backend.config.RateLimitService;
import com.cryptoexchange.backend.config.SecurityConfig;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({JacksonConfig.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void register_Success() throws Exception {
        // Given
        UserAccount user = new UserAccount("testuser", "test@example.com", "hashed");
        user.setId(UUID.randomUUID());
        String token = "test-jwt-token";

        when(authService.register(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class))).thenReturn(token);

        AuthController.AuthRegisterRequest request = new AuthController.AuthRegisterRequest();
        request.login = "testuser";
        request.email = "test@example.com";
        request.password = "Password123!";

        // When/Then - CSRF is disabled in SecurityConfig, so we don't need csrf() token
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value(token))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).register("testuser", "test@example.com", "Password123!");
        verify(jwtService).generateToken(user.getId());
    }

    @Test
    void register_DuplicateUser_ReturnsConflict() throws Exception {
        // Given
        when(authService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("User with login testuser already exists"));

        AuthController.AuthRegisterRequest request = new AuthController.AuthRegisterRequest();
        request.login = "testuser";
        request.email = "test@example.com";
        request.password = "Password123!";

        // When/Then - CSRF is disabled in SecurityConfig, so we don't need csrf() token
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already exists"));
    }

    @Test
    void login_Success() throws Exception {
        // Given
        UserAccount user = new UserAccount("testuser", "test@example.com", "hashed");
        user.setId(UUID.randomUUID());
        String token = "test-jwt-token";

        when(authService.authenticate(anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class))).thenReturn(token);

        AuthController.AuthLoginRequest request = new AuthController.AuthLoginRequest();
        request.loginOrEmail = "testuser";
        request.password = "Password123!";

        // When/Then - CSRF is disabled in SecurityConfig, so we don't need csrf() token
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(token))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).authenticate("testuser", "Password123!");
        verify(jwtService).generateToken(user.getId());
    }

    @Test
    void login_InvalidCredentials_ReturnsUnauthorized() throws Exception {
        // Given
        when(authService.authenticate(anyString(), anyString()))
                .thenThrow(new com.cryptoexchange.backend.domain.exception.NotFoundException("Invalid credentials"));

        AuthController.AuthLoginRequest request = new AuthController.AuthLoginRequest();
        request.loginOrEmail = "testuser";
        request.password = "WrongPassword";

        // When/Then - CSRF is disabled in SecurityConfig, so we don't need csrf() token
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void register_ValidationError_ReturnsBadRequest() throws Exception {
        // Given - missing required fields
        AuthController.AuthRegisterRequest request = new AuthController.AuthRegisterRequest();
        request.login = ""; // Invalid
        request.email = "invalid-email"; // Invalid
        request.password = "short"; // Too short

        // When/Then - CSRF is disabled in SecurityConfig, so we don't need csrf() token
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
