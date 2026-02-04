package com.cryptoexchange.backend.domain.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal Spring ApplicationEvent published when a trade is executed.
 * This event is published synchronously and will trigger Kafka publishing after DB commit.
 */
public class DomainTradeExecuted extends ApplicationEvent {
    private final UUID tradeId;
    private final String marketSymbol;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Instant executedAt;

    public DomainTradeExecuted(Object source, UUID tradeId, String marketSymbol, 
                               BigDecimal price, BigDecimal quantity, Instant executedAt) {
        super(source);
        this.tradeId = tradeId;
        this.marketSymbol = marketSymbol;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public String getMarketSymbol() {
        return marketSymbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
