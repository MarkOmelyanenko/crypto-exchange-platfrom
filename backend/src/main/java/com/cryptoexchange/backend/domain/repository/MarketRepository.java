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
    List<Market> findByActiveTrue();
}
