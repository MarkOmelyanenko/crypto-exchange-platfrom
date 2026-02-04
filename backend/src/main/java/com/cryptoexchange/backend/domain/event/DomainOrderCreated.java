package com.cryptoexchange.backend.domain.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Internal Spring ApplicationEvent published when an order is created.
 * This event is published synchronously and will trigger Kafka publishing after DB commit.
 */
public class DomainOrderCreated extends ApplicationEvent {
    private final UUID orderId;
    private final String marketSymbol;
    private final String side;

    public DomainOrderCreated(Object source, UUID orderId, String marketSymbol, String side) {
        super(source);
        this.orderId = orderId;
        this.marketSymbol = marketSymbol;
        this.side = side;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getMarketSymbol() {
        return marketSymbol;
    }

    public String getSide() {
        return side;
    }
}
