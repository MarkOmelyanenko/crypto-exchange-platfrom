package com.cryptoexchange.backend.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event published when a trade is executed.
 */
public class TradeExecutedEvent {
    private UUID tradeId;
    private UUID marketId;
    private String marketSymbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal quoteAmount;
    private OffsetDateTime executedAt;

    public TradeExecutedEvent() {
    }

    public TradeExecutedEvent(UUID tradeId, UUID marketId, String marketSymbol, 
                              BigDecimal price, BigDecimal quantity, BigDecimal quoteAmount,
                              OffsetDateTime executedAt) {
        this.tradeId = tradeId;
        this.marketId = marketId;
        this.marketSymbol = marketSymbol;
        this.price = price;
        this.quantity = quantity;
        this.quoteAmount = quoteAmount;
        this.executedAt = executedAt;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public void setTradeId(UUID tradeId) {
        this.tradeId = tradeId;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getQuoteAmount() {
        return quoteAmount;
    }

    public void setQuoteAmount(BigDecimal quoteAmount) {
        this.quoteAmount = quoteAmount;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }

    @Override
    public String toString() {
        return "TradeExecutedEvent{" +
                "tradeId=" + tradeId +
                ", marketId=" + marketId +
                ", marketSymbol='" + marketSymbol + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", quoteAmount=" + quoteAmount +
                ", executedAt=" + executedAt +
                '}';
    }
}
