package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserAccount testUser;
    private UUID testUserId;
    private String hashedPassword = "$2a$10$hashedPassword";

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new UserAccount("testuser", "test@example.com", hashedPassword);
        testUser.setId(testUserId);
    }

    @Test
    void getUser_Success() {
        // Given
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        UserAccount result = userService.getUser(testUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUserId);
        assertThat(result.getLogin()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void getUser_NotFound_ThrowsException() {
        // Given
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.getUser(testUserId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateProfile_Success_UpdateBoth() {
        // Given
        String newLogin = "newuser";
        String newEmail = "newemail@example.com";
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userAccountRepository.findByLogin(newLogin)).thenReturn(Optional.empty());
        when(userAccountRepository.findByEmail(newEmail.toLowerCase())).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(testUser);

        // When
        UserAccount result = userService.updateProfile(testUserId, newLogin, newEmail);

        // Then
        assertThat(result).isNotNull();
        verify(userAccountRepository).findByLogin(newLogin);
        verify(userAccountRepository).findByEmail(newEmail.toLowerCase());
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    void updateProfile_Success_NoChanges() {
        // Given
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(testUser);

        // When
        UserAccount result = userService.updateProfile(testUserId, "testuser", "test@example.com");

        // Then
        assertThat(result).isNotNull();
        verify(userAccountRepository, never()).findByLogin(anyString());
        verify(userAccountRepository, never()).findByEmail(anyString());
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    void updateProfile_DuplicateLogin_ThrowsException() {
        // Given
        String newLogin = "existinguser";
        UserAccount existingUser = new UserAccount("existinguser", "other@example.com", hashedPassword);
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userAccountRepository.findByLogin(newLogin)).thenReturn(Optional.of(existingUser));

        // When/Then
        assertThatThrownBy(() -> userService.updateProfile(testUserId, newLogin, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void updateProfile_DuplicateEmail_ThrowsException() {
        // Given
        String newEmail = "existing@example.com";
        UserAccount existingUser = new UserAccount("otheruser", newEmail, hashedPassword);
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userAccountRepository.findByEmail(newEmail.toLowerCase())).thenReturn(Optional.of(existingUser));

        // When/Then
        assertThatThrownBy(() -> userService.updateProfile(testUserId, "testuser", newEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void changePassword_Success() {
        // Given
        String currentPassword = "CurrentPass123!";
        String newPassword = "NewPass123!";
        String newHashedPassword = "$2a$10$newHashedPassword";
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(currentPassword, hashedPassword)).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(newHashedPassword);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(testUser);

        // When
        userService.changePassword(testUserId, currentPassword, newPassword);

        // Then
        verify(passwordEncoder).matches(currentPassword, hashedPassword);
        verify(passwordEncoder).encode(newPassword);
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsException() {
        // Given
        String wrongPassword = "WrongPass123!";
        String newPassword = "NewPass123!";
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, hashedPassword)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> userService.changePassword(testUserId, wrongPassword, newPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void changePassword_TooShortNewPassword_ThrowsException() {
        // Given
        String currentPassword = "CurrentPass123!";
        String shortPassword = "short";
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(currentPassword, hashedPassword)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> userService.changePassword(testUserId, currentPassword, shortPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void changePassword_NullNewPassword_ThrowsException() {
        // Given
        String currentPassword = "CurrentPass123!";
        when(userAccountRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(currentPassword, hashedPassword)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> userService.changePassword(testUserId, currentPassword, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userAccountRepository, never()).save(any());
    }
}
