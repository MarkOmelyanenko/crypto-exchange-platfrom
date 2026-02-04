package com.cryptoexchange.backend.domain.event;

import java.util.UUID;

/**
 * Event published when a new order is created and ready for matching.
 */
public class OrderCreatedEvent {
    private UUID orderId;
    private UUID marketId;
    private String marketSymbol;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(UUID orderId, UUID marketId, String marketSymbol) {
        this.orderId = orderId;
        this.marketId = marketId;
        this.marketSymbol = marketSymbol;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getMarketId() {
        return marketId;
    }

    public void setMarketId(UUID marketId) {
        this.marketId = marketId;
    }

    public String getMarketSymbol() {
        return marketSymbol;
    }

    public void setMarketSymbol(String marketSymbol) {
        this.marketSymbol = marketSymbol;
    }

    @Override
    public String toString() {
        return "OrderCreatedEvent{" +
                "orderId=" + orderId +
                ", marketId=" + marketId +
                ", marketSymbol='" + marketSymbol + '\'' +
                '}';
    }
}
