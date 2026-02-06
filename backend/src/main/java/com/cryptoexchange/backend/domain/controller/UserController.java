package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User management endpoints.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management endpoints")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the current authenticated user information")
    public ResponseEntity<UserMeResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            UUID userId = UUID.fromString(authentication.getPrincipal().toString());
            UserAccount user = userService.getUser(userId);
            return ResponseEntity.ok(new UserMeResponse(
                user.getId().toString(),
                user.getLogin(),
                user.getEmail()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Updates login and email for the current authenticated user")
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            UUID userId = UUID.fromString(authentication.getPrincipal().toString());
            UserAccount user = userService.updateProfile(userId, request.login, request.email);
            return ResponseEntity.ok(new UserMeResponse(
                user.getId().toString(),
                user.getLogin(),
                user.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Update failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Update failed", e.getMessage()));
        }
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change password", description = "Changes the password for the current authenticated user")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            UUID userId = UUID.fromString(authentication.getPrincipal().toString());
            userService.changePassword(userId, request.currentPassword, request.newPassword);
            return ResponseEntity.ok(new SuccessResponse("Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Password change failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Password change failed", e.getMessage()));
        }
    }

    // DTOs
    public static class UserMeResponse {
        public String id;
        public String login;
        public String email;

        public UserMeResponse(String id, String login, String email) {
            this.id = id;
            this.login = login;
            this.email = email;
        }
    }

    public static class UpdateProfileRequest {
        @NotBlank(message = "Login is required")
        @Size(min = 3, max = 50, message = "Login must be between 3 and 50 characters")
        public String login;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        public String email;
    }

    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        public String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        public String newPassword;
    }

    public static class SuccessResponse {
        public String message;

        public SuccessResponse(String message) {
            this.message = message;
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
