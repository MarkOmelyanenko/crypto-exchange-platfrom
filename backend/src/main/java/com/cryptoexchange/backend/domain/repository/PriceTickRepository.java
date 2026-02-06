package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.PriceTick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceTickRepository extends JpaRepository<PriceTick, UUID> {

    /**
     * Find the most recent price tick for a symbol.
     */
    Optional<PriceTick> findFirstBySymbolOrderByTsDesc(String symbol);

    /**
     * Find price ticks for a symbol within a time range, ordered by timestamp ascending.
     */
    @Query("SELECT p FROM PriceTick p WHERE p.symbol = :symbol " +
           "AND p.ts >= :from AND p.ts <= :to ORDER BY p.ts ASC")
    List<PriceTick> findBySymbolAndTsBetween(
        @Param("symbol") String symbol,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );

    /**
     * Find latest price ticks for multiple symbols (one per symbol).
     */
    @Query("SELECT p FROM PriceTick p WHERE p.ts = " +
           "(SELECT MAX(p2.ts) FROM PriceTick p2 WHERE p2.symbol = p.symbol) " +
           "AND p.symbol IN :symbols")
    List<PriceTick> findLatestBySymbols(@Param("symbols") List<String> symbols);
}
