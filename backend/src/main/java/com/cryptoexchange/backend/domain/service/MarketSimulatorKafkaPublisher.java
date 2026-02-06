package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.event.MarketTickEvent;
import com.cryptoexchange.backend.domain.event.MarketTradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes market simulator events to Kafka.
 * Only active when Kafka is configured.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class MarketSimulatorKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(MarketSimulatorKafkaPublisher.class);

    private final KafkaTemplate<String, MarketTickEvent> tickKafkaTemplate;
    private final KafkaTemplate<String, MarketTradeEvent> tradeKafkaTemplate;
    private final String ticksTopic;
    private final String tradesTopic;

    public MarketSimulatorKafkaPublisher(
            KafkaTemplate<String, MarketTickEvent> tickKafkaTemplate,
            KafkaTemplate<String, MarketTradeEvent> tradeKafkaTemplate,
            @Value("${app.kafka.topics.market-ticks:market.ticks}") String ticksTopic,
            @Value("${app.kafka.topics.market-trades:market.trades}") String tradesTopic) {
        this.tickKafkaTemplate = tickKafkaTemplate;
        this.tradeKafkaTemplate = tradeKafkaTemplate;
        this.ticksTopic = ticksTopic;
        this.tradesTopic = tradesTopic;
    }

    public void publishTick(String marketSymbol, MarketSimulationEngine.TickData tickData) {
        try {
            MarketTickEvent event = new MarketTickEvent(
                marketSymbol,
                tickData.ts,
                tickData.lastPrice,
                tickData.bid,
                tickData.ask,
                tickData.volume
            );
            
            // Key by marketSymbol for partition affinity
            tickKafkaTemplate.send(ticksTopic, marketSymbol, event);
            log.debug("Published MarketTickEvent to Kafka for market {}", marketSymbol);
        } catch (Exception e) {
            log.error("Failed to publish MarketTickEvent to Kafka for market {}: {}", 
                marketSymbol, e.getMessage(), e);
        }
    }

    public void publishTrade(String marketSymbol, MarketSimulationEngine.TradeData tradeData) {
        try {
            MarketTradeEvent event = new MarketTradeEvent(
                marketSymbol,
                tradeData.ts,
                tradeData.price,
                tradeData.qty,
                tradeData.side.name()
            );
            
            // Key by marketSymbol for partition affinity
            tradeKafkaTemplate.send(tradesTopic, marketSymbol, event);
            log.debug("Published MarketTradeEvent to Kafka for market {}", marketSymbol);
        } catch (Exception e) {
            log.error("Failed to publish MarketTradeEvent to Kafka for market {}: {}", 
                marketSymbol, e.getMessage(), e);
        }
    }
}
