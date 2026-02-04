package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.MarketTrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MarketTradeRepository extends JpaRepository<MarketTrade, UUID> {
    
    List<MarketTrade> findTop50ByMarketSymbolOrderByTsDesc(String marketSymbol);
    
    @Query("SELECT t FROM MarketTrade t WHERE t.marketSymbol = :symbol " +
           "AND t.ts >= :from AND t.ts <= :to ORDER BY t.ts DESC")
    Page<MarketTrade> findByMarketSymbolAndTsBetween(
        @Param("symbol") String symbol,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to,
        Pageable pageable
    );
}
