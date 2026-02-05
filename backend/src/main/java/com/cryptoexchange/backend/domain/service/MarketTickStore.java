package com.cryptoexchange.backend.domain.service;

import java.util.List;

/**
 * Interface for storing market tick data.
 * Allows decoupling from JPA repositories for testing.
 */
public interface MarketTickStore {
    void saveAll(List<com.cryptoexchange.backend.domain.model.MarketTick> ticks);
    com.cryptoexchange.backend.domain.model.MarketTick findLatest(String marketSymbol);
    List<com.cryptoexchange.backend.domain.model.MarketTick> findRange(String marketSymbol, 
                                                                       java.time.OffsetDateTime from, 
                                                                       java.time.OffsetDateTime to,
                                                                       int page, int size);
}
