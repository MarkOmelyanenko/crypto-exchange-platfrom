package com.cryptoexchange.backend.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to Kafka when a new order is created and ready for matching.
 */
public record OrderCreatedEvent(
    UUID orderId,
    String marketSymbol,
    String side,
    Instant createdAt
) {
    public OrderCreatedEvent {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }
        if (marketSymbol == null || marketSymbol.isBlank()) {
            throw new IllegalArgumentException("marketSymbol cannot be null or blank");
        }
    }
}
