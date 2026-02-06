package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void getCurrentUser_Success() throws Exception {
        // Given
        UserAccount user = new UserAccount("testuser", "test@example.com", "hashed");
        user.setId(USER_ID);
        when(userService.getUser(USER_ID)).thenReturn(user);

        // When/Then
        mockMvc.perform(get("/api/users/me")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.login").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userService).getUser(USER_ID);
    }

    @Test
    void getCurrentUser_Unauthorized() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUser(any());
    }

    @Test
    void updateProfile_Success() throws Exception {
        // Given
        UserAccount updatedUser = new UserAccount("newuser", "newemail@example.com", "hashed");
        updatedUser.setId(USER_ID);
        when(userService.updateProfile(eq(USER_ID), eq("newuser"), eq("newemail@example.com")))
                .thenReturn(updatedUser);

        UserController.UpdateProfileRequest request = new UserController.UpdateProfileRequest();
        request.login = "newuser";
        request.email = "newemail@example.com";

        // When/Then
        mockMvc.perform(put("/api/users/me")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.login").value("newuser"))
                .andExpect(jsonPath("$.email").value("newemail@example.com"));

        verify(userService).updateProfile(USER_ID, "newuser", "newemail@example.com");
    }

    @Test
    void updateProfile_DuplicateLogin_ReturnsConflict() throws Exception {
        // Given
        when(userService.updateProfile(eq(USER_ID), eq("existinguser"), anyString()))
                .thenThrow(new IllegalArgumentException("User with login existinguser already exists"));

        UserController.UpdateProfileRequest request = new UserController.UpdateProfileRequest();
        request.login = "existinguser";
        request.email = "test@example.com";

        // When/Then
        mockMvc.perform(put("/api/users/me")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Update failed"));

        verify(userService).updateProfile(USER_ID, "existinguser", "test@example.com");
    }

    @Test
    void updateProfile_DuplicateEmail_ReturnsConflict() throws Exception {
        // Given
        when(userService.updateProfile(eq(USER_ID), anyString(), eq("existing@example.com")))
                .thenThrow(new IllegalArgumentException("User with email existing@example.com already exists"));

        UserController.UpdateProfileRequest request = new UserController.UpdateProfileRequest();
        request.login = "testuser";
        request.email = "existing@example.com";

        // When/Then
        mockMvc.perform(put("/api/users/me")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Update failed"));

        verify(userService).updateProfile(USER_ID, "testuser", "existing@example.com");
    }

    @Test
    void updateProfile_ValidationError_ReturnsBadRequest() throws Exception {
        // Given - invalid email format
        UserController.UpdateProfileRequest request = new UserController.UpdateProfileRequest();
        request.login = "testuser";
        request.email = "invalid-email"; // Invalid

        // When/Then
        mockMvc.perform(put("/api/users/me")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(any(), anyString(), anyString());
    }

    @Test
    void updateProfile_Unauthorized() throws Exception {
        // Given
        UserController.UpdateProfileRequest request = new UserController.UpdateProfileRequest();
        request.login = "newuser";
        request.email = "newemail@example.com";

        // When/Then
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateProfile(any(), anyString(), anyString());
    }

    @Test
    void changePassword_Success() throws Exception {
        // Given
        doNothing().when(userService).changePassword(eq(USER_ID), eq("CurrentPass123!"), eq("NewPass123!"));

        UserController.ChangePasswordRequest request = new UserController.ChangePasswordRequest();
        request.currentPassword = "CurrentPass123!";
        request.newPassword = "NewPass123!";

        // When/Then
        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(userService).changePassword(USER_ID, "CurrentPass123!", "NewPass123!");
    }

    @Test
    void changePassword_WrongCurrentPassword_ReturnsBadRequest() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(userService).changePassword(eq(USER_ID), eq("WrongPass123!"), anyString());

        UserController.ChangePasswordRequest request = new UserController.ChangePasswordRequest();
        request.currentPassword = "WrongPass123!";
        request.newPassword = "NewPass123!";

        // When/Then
        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password change failed"))
                .andExpect(jsonPath("$.errors").value("Current password is incorrect"));

        verify(userService).changePassword(USER_ID, "WrongPass123!", "NewPass123!");
    }

    @Test
    void changePassword_TooShortNewPassword_ReturnsBadRequest() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("New password must be at least 8 characters"))
                .when(userService).changePassword(eq(USER_ID), anyString(), eq("short"));

        UserController.ChangePasswordRequest request = new UserController.ChangePasswordRequest();
        request.currentPassword = "CurrentPass123!";
        request.newPassword = "short";

        // When/Then
        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password change failed"));

        verify(userService).changePassword(USER_ID, "CurrentPass123!", "short");
    }

    @Test
    void changePassword_ValidationError_ReturnsBadRequest() throws Exception {
        // Given - missing required fields
        UserController.ChangePasswordRequest request = new UserController.ChangePasswordRequest();
        request.currentPassword = ""; // Invalid
        request.newPassword = "short"; // Too short

        // When/Then
        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), anyString(), anyString());
    }

    @Test
    void changePassword_Unauthorized() throws Exception {
        // Given
        UserController.ChangePasswordRequest request = new UserController.ChangePasswordRequest();
        request.currentPassword = "CurrentPass123!";
        request.newPassword = "NewPass123!";

        // When/Then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).changePassword(any(), anyString(), anyString());
    }
}
