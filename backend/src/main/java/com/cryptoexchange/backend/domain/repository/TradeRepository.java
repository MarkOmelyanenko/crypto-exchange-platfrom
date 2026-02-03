package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findAllByMarketIdOrderByExecutedAtDesc(UUID marketId);
}
