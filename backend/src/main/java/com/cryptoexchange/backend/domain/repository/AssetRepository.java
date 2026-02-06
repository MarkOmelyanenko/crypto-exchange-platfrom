package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findBySymbol(String symbol);

    Optional<Asset> findBySymbolIgnoreCase(String symbol);

    @Query("SELECT a FROM Asset a WHERE " +
           "LOWER(a.symbol) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Asset> searchByQuery(@Param("q") String query);
}
