package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.config.JwtService;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user authentication and registration.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>User registration with email and password</li>
 *   <li>User login with login/email and password</li>
 * </ul>
 * 
 * <p>All endpoints are publicly accessible (no authentication required).
 * Returns JWT tokens upon successful authentication.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
@Validated
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRegisterRequest request) {
        try {
            UserAccount user = authService.register(request.login, request.email, request.password);
            String token = jwtService.generateToken(user.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthTokenResponse(token, "Bearer"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("User already exists", e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates a user and returns a token")
    public ResponseEntity<?> login(@Valid @RequestBody AuthLoginRequest request) {
        try {
            UserAccount user = authService.authenticate(request.loginOrEmail, request.password);
            String token = jwtService.generateToken(user.getId());
            return ResponseEntity.ok(new AuthTokenResponse(token, "Bearer"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid credentials", null));
        }
    }

    // DTOs
    public static class AuthRegisterRequest {
        @NotBlank(message = "Login is required")
        @Size(min = 3, max = 50, message = "Login must be between 3 and 50 characters")
        public String login;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        public String email;
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        public String password;
    }

    public static class AuthLoginRequest {
        @NotBlank(message = "Login or email is required")
        public String loginOrEmail;
        
        @NotBlank(message = "Password is required")
        public String password;
    }

    public static class AuthTokenResponse {
        public String accessToken;
        public String tokenType;

        public AuthTokenResponse(String accessToken, String tokenType) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
        }
    }

    public static class ErrorResponse {
        public String message;
        public String errors;

        public ErrorResponse(String message, String errors) {
            this.message = message;
            this.errors = errors;
        }
    }
}
