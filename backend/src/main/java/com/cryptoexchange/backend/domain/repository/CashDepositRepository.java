package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.CashDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface CashDepositRepository extends JpaRepository<CashDeposit, UUID> {

    /**
     * Returns the total amount deposited by a user since the given timestamp.
     * Used to enforce the rolling 24-hour deposit limit.
     */
    @Query("SELECT COALESCE(SUM(d.amountUsd), 0) FROM CashDeposit d WHERE d.user.id = :userId AND d.createdAt >= :since")
    BigDecimal sumDepositsSince(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);
}
