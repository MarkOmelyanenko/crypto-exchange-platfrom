package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findAllByMarketIdOrderByExecutedAtDesc(UUID marketId);
    
    @Query("SELECT t FROM Trade t WHERE t.market.id = :marketId ORDER BY t.executedAt DESC")
    Page<Trade> findAllByMarketId(@Param("marketId") UUID marketId, Pageable pageable);
}
