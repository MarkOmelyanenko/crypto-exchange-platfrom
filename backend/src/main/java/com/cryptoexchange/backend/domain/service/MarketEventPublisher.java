package com.cryptoexchange.backend.domain.service;

/**
 * Interface for publishing market events.
 * Allows decoupling from Kafka for testing.
 */
public interface MarketEventPublisher {
    void publishTick(String marketSymbol, MarketSimulationEngine.TickData tickData);
    void publishTrade(String marketSymbol, MarketSimulationEngine.TradeData tradeData);
}
