package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserAccountRepository userAccountRepository;

    public UserService(final UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
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
}
