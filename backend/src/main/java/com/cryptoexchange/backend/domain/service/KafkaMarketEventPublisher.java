package com.cryptoexchange.backend.domain.service;

import org.springframework.stereotype.Component;

/**
 * Kafka-based implementation of MarketEventPublisher.
 */
@Component
public class KafkaMarketEventPublisher implements MarketEventPublisher {
    
    private final MarketSimulatorKafkaPublisher kafkaPublisher;
    
    public KafkaMarketEventPublisher(MarketSimulatorKafkaPublisher kafkaPublisher) {
        this.kafkaPublisher = kafkaPublisher;
    }
    
    @Override
    public void publishTick(String marketSymbol, MarketSimulationEngine.TickData tickData) {
        kafkaPublisher.publishTick(marketSymbol, tickData);
    }
    
    @Override
    public void publishTrade(String marketSymbol, MarketSimulationEngine.TradeData tradeData) {
        kafkaPublisher.publishTrade(marketSymbol, tradeData);
    }
}
