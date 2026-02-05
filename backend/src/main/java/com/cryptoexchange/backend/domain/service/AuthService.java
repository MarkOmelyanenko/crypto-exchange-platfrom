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
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount register(String login, String email, String password) {
        // Check if login already exists
        if (userAccountRepository.findByLogin(login).isPresent()) {
            throw new IllegalArgumentException("User with login " + login + " already exists");
        }

        // Check if email already exists
        if (userAccountRepository.findByEmail(email.toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(password);

        // Create user
        UserAccount user = new UserAccount(login, email.toLowerCase(), passwordHash);
        UserAccount saved = userAccountRepository.save(user);
        log.info("Registered user: {} (id: {})", login, saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public UserAccount authenticate(String loginOrEmail, String password) {
        // Try to find user by login or email (case-insensitive for email)
        UserAccount user = userAccountRepository.findByLoginOrEmail(loginOrEmail)
                .orElseThrow(() -> new NotFoundException("Invalid credentials"));

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new NotFoundException("Invalid credentials");
        }

        log.debug("Authenticated user: {} (id: {})", user.getLogin(), user.getId());
        return user;
    }

    @Transactional(readOnly = true)
    public UserAccount getUserById(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }
}
