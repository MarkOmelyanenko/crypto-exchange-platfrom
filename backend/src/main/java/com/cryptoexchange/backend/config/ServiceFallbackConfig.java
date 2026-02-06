package com.cryptoexchange.backend.config;

import com.cryptoexchange.backend.domain.service.MarketEventPublisher;
import com.cryptoexchange.backend.domain.service.MarketSimulationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides no-op fallback beans for optional services (Kafka, etc.).
 * These are only used when the real implementations are not available
 * (e.g., Kafka is not configured).
 */
@Configuration
public class ServiceFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(ServiceFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(MarketEventPublisher.class)
    public MarketEventPublisher noOpMarketEventPublisher() {
        log.info("Kafka not configured — using no-op MarketEventPublisher");
        return new MarketEventPublisher() {
            @Override
            public void publishTick(String marketSymbol, MarketSimulationEngine.TickData tickData) {
                log.debug("Kafka not configured — skipping tick publish for {}", marketSymbol);
            }

            @Override
            public void publishTrade(String marketSymbol, MarketSimulationEngine.TradeData tradeData) {
                log.debug("Kafka not configured — skipping trade publish for {}", marketSymbol);
            }
        };
    }
}
