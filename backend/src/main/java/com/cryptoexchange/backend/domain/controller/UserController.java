package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<UserMeResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) UUID userId) {
        // TODO: Extract userId from JWT token in production
        // For now, extract from dev token format "dev-token-{userId}" or use query parameter
        UUID targetUserId = userId;
        
        if (targetUserId == null && authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            if (token.startsWith("dev-token-")) {
                try {
                    String userIdStr = token.substring("dev-token-".length());
                    targetUserId = UUID.fromString(userIdStr);
                } catch (Exception e) {
                    // Invalid token format, ignore
                }
            }
        }
        
        if (targetUserId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        
        UserAccount user = userService.getUser(targetUserId);
        return ResponseEntity.ok(new UserMeResponse(
            user.getId().toString(),
            user.getEmail(),
            user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
            user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null
        ));
    }

    // DTO
    public static class UserMeResponse {
        public String id;
        public String email;
        public String createdAt;
        public String updatedAt;

        public UserMeResponse(String id, String email, String createdAt, String updatedAt) {
            this.id = id;
            this.email = email;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
