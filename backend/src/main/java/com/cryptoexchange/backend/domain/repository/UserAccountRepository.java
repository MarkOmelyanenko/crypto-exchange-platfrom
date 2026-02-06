package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByLogin(String login);
    
    @Query("SELECT u FROM UserAccount u WHERE u.login = :loginOrEmail OR LOWER(u.email) = LOWER(:loginOrEmail)")
    Optional<UserAccount> findByLoginOrEmail(@Param("loginOrEmail") String loginOrEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.id = :id")
    Optional<UserAccount> findByIdWithLock(@Param("id") UUID id);
}
