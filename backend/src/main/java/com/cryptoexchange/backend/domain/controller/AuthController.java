package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints for login and registration.
 * TODO: Implement proper JWT authentication in production.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
@Validated
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account")
    public ResponseEntity<AuthRegisterResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        try {
            var user = userService.createUser(request.email);
            // For now, return a simple token (in production, use JWT)
            String token = "dev-token-" + user.getId();
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthRegisterResponse(user.getId().toString(), user.getEmail(), token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthRegisterResponse(null, null, null, e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates a user and returns a token")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        try {
            var user = userService.getUserByEmail(request.email);
            // For now, return a simple token (in production, use JWT with password verification)
            String token = "dev-token-" + user.getId();
            return ResponseEntity.ok(new AuthLoginResponse(user.getId().toString(), user.getEmail(), token));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthLoginResponse(null, null, null, "Invalid credentials"));
        }
    }

    // DTOs
    public static class AuthRegisterRequest {
        @NotBlank(message = "Email is required")
        public String email;
        
        @NotBlank(message = "Password is required")
        public String password;
    }

    public static class AuthRegisterResponse {
        public String userId;
        public String email;
        public String token;
        public String message;

        public AuthRegisterResponse(String userId, String email, String token) {
            this.userId = userId;
            this.email = email;
            this.token = token;
        }

        public AuthRegisterResponse(String userId, String email, String token, String message) {
            this.userId = userId;
            this.email = email;
            this.token = token;
            this.message = message;
        }
    }

    public static class AuthLoginRequest {
        @NotBlank(message = "Email is required")
        public String email;
        
        @NotBlank(message = "Password is required")
        public String password;
    }

    public static class AuthLoginResponse {
        public String userId;
        public String email;
        public String token;
        public String message;

        public AuthLoginResponse(String userId, String email, String token) {
            this.userId = userId;
            this.email = email;
            this.token = token;
        }

        public AuthLoginResponse(String userId, String email, String token, String message) {
            this.userId = userId;
            this.email = email;
            this.token = token;
            this.message = message;
        }
    }
}
