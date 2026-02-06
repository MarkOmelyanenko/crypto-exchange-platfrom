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
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private UserAccount testUser;
    private String testPassword = "Password123!";
    private String hashedPassword = "$2a$10$hashedPassword";

    @BeforeEach
    void setUp() {
        testUser = new UserAccount("testuser", "test@example.com", hashedPassword);
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void register_Success() {
        // Given
        when(userAccountRepository.findByLogin("testuser")).thenReturn(Optional.empty());
        when(userAccountRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(testPassword)).thenReturn(hashedPassword);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(testUser);

        // When
        UserAccount result = authService.register("testuser", "test@example.com", testPassword);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLogin()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(passwordEncoder).encode(testPassword);
    }

    @Test
    void register_DuplicateLogin_ThrowsException() {
        // Given
        when(userAccountRepository.findByLogin("testuser")).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.register("testuser", "test@example.com", testPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        // Given
        when(userAccountRepository.findByLogin("testuser")).thenReturn(Optional.empty());
        when(userAccountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.register("testuser", "test@example.com", testPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void authenticate_Success_ByLogin() {
        // Given
        when(userAccountRepository.findByLoginOrEmail("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, hashedPassword)).thenReturn(true);

        // When
        UserAccount result = authService.authenticate("testuser", testPassword);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLogin()).isEqualTo("testuser");
        verify(passwordEncoder).matches(testPassword, hashedPassword);
    }

    @Test
    void authenticate_Success_ByEmail() {
        // Given
        when(userAccountRepository.findByLoginOrEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, hashedPassword)).thenReturn(true);

        // When
        UserAccount result = authService.authenticate("test@example.com", testPassword);

        // Then
        assertThat(result).isNotNull();
        verify(passwordEncoder).matches(testPassword, hashedPassword);
    }

    @Test
    void authenticate_UserNotFound_ThrowsException() {
        // Given
        when(userAccountRepository.findByLoginOrEmail(anyString())).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.authenticate("testuser", testPassword))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Invalid credentials");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void authenticate_WrongPassword_ThrowsException() {
        // Given
        when(userAccountRepository.findByLoginOrEmail("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, hashedPassword)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.authenticate("testuser", testPassword))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Invalid credentials");
        verify(passwordEncoder).matches(testPassword, hashedPassword);
    }
}
