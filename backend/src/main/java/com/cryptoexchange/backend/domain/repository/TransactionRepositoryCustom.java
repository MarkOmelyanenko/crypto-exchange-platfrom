package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Custom repository interface for dynamic transaction filtering.
 * Avoids Hibernate/PostgreSQL null-parameter type inference issues with JPQL.
 */
public interface TransactionRepositoryCustom {

    Page<Transaction> findFiltered(
            UUID userId,
            String symbol,
            OrderSide side,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );
}
