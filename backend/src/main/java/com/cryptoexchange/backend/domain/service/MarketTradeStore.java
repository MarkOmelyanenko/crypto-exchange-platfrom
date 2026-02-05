package com.cryptoexchange.backend.domain.service;

import java.util.List;

/**
 * Interface for storing market trade data.
 * Allows decoupling from JPA repositories for testing.
 */
public interface MarketTradeStore {
    void saveAll(List<com.cryptoexchange.backend.domain.model.MarketTrade> trades);
    List<com.cryptoexchange.backend.domain.model.MarketTrade> findRecent(String marketSymbol, int limit);
}
