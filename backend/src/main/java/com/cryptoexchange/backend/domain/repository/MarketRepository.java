package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketRepository extends JpaRepository<Market, UUID> {
    Optional<Market> findBySymbol(String symbol);
    
    @org.springframework.data.jpa.repository.Query("SELECT m FROM Market m JOIN FETCH m.baseAsset JOIN FETCH m.quoteAsset WHERE m.active = true")
    List<Market> findByActiveTrue();
}
