package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(final UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Deprecated
    public UserAccount createUser(String email) {
        if (userAccountRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }
        // This method is deprecated - use AuthService.register instead
        throw new UnsupportedOperationException("Use AuthService.register instead");
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(UUID userId) {
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    @Transactional(readOnly = true)
    public UserAccount getUserByEmail(String email) {
        return userAccountRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
    }

    /**
     * Update user profile (login and email).
     * Validates uniqueness of login and email before updating.
     */
    public UserAccount updateProfile(UUID userId, String newLogin, String newEmail) {
        UserAccount user = getUser(userId);

        // Check if login is being changed and if new login already exists
        if (!user.getLogin().equals(newLogin)) {
            if (userAccountRepository.findByLogin(newLogin).isPresent()) {
                throw new IllegalArgumentException("User with login " + newLogin + " already exists");
            }
            user.setLogin(newLogin);
            log.info("Updated login for user {} (id: {})", newLogin, userId);
        }

        // Check if email is being changed and if new email already exists
        String normalizedEmail = newEmail.toLowerCase();
        if (!user.getEmail().equals(normalizedEmail)) {
            if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
                throw new IllegalArgumentException("User with email " + newEmail + " already exists");
            }
            user.setEmail(normalizedEmail);
            log.info("Updated email for user {} (id: {})", normalizedEmail, userId);
        }

        return userAccountRepository.save(user);
    }

    /**
     * Change user password.
     * Verifies current password before updating to new password.
     */
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = getUser(userId);

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Validate new password length (same as registration requirement)
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }

        // Hash and update password
        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newPasswordHash);
        userAccountRepository.save(user);
        log.info("Password changed for user {} (id: {})", user.getLogin(), userId);
    }
}
