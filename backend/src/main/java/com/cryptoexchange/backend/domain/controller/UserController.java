package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
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
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
    }

    // DTO
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
}
