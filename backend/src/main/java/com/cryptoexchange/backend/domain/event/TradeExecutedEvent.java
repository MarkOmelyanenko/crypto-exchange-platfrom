package com.cryptoexchange.backend.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published to Kafka when a trade is executed and persisted.
 */
public record TradeExecutedEvent(
    UUID tradeId,
    String marketSymbol,
    BigDecimal price,
    BigDecimal quantity,
    Instant executedAt
) {
    public TradeExecutedEvent {
        if (tradeId == null) {
            throw new IllegalArgumentException("tradeId cannot be null");
        }
        if (marketSymbol == null || marketSymbol.isBlank()) {
            throw new IllegalArgumentException("marketSymbol cannot be null or blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (executedAt == null) {
            throw new IllegalArgumentException("executedAt cannot be null");
        }
    }
}
