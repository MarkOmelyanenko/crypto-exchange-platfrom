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

/**
 * Service for user authentication and registration.
 * 
 * <p>Handles user registration with duplicate checking (login and email must be unique),
 * password hashing, and user authentication. All operations are transactional.
 * 
 * <p>Throws {@link IllegalArgumentException} if registration fails due to duplicate credentials.
 * Throws {@link NotFoundException} if authentication fails (user not found or invalid password).
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user account.
     * 
     * @param login unique login username
     * @param email unique email address (stored in lowercase)
     * @param password plain text password (will be hashed)
     * @return the created user account
     * @throws IllegalArgumentException if login or email already exists
     */
    public UserAccount register(String login, String email, String password) {
        if (userAccountRepository.findByLogin(login).isPresent()) {
            throw new IllegalArgumentException("User with login " + login + " already exists");
        }

        if (userAccountRepository.findByEmail(email.toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        String passwordHash = passwordEncoder.encode(password);
        UserAccount user = new UserAccount(login, email.toLowerCase(), passwordHash);
        UserAccount saved = userAccountRepository.save(user);
        log.info("Registered user: {} (id: {})", login, saved.getId());
        return saved;
    }

    /**
     * Authenticates a user by login/email and password.
     * 
     * @param loginOrEmail user login or email (case-insensitive for email)
     * @param password plain text password
     * @return the authenticated user account
     * @throws NotFoundException if user not found or password is invalid
     */
    @Transactional(readOnly = true)
    public UserAccount authenticate(String loginOrEmail, String password) {
        UserAccount user = userAccountRepository.findByLoginOrEmail(loginOrEmail)
                .orElseThrow(() -> new NotFoundException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new NotFoundException("Invalid credentials");
        }

        log.debug("Authenticated user: {} (id: {})", user.getLogin(), user.getId());
        return user;
    }

    /**
     * Retrieves a user by ID.
     * 
     * @param userId the user ID
     * @return the user account
     * @throws NotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserAccount getUserById(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }
}
