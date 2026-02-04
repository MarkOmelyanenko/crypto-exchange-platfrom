package com.cryptoexchange.backend.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Kafka event for simulated market trades.
 */
public class MarketTradeEvent {
    private String marketSymbol;
    private OffsetDateTime ts;
    private BigDecimal price;
    private BigDecimal qty;
    private String side;

    public MarketTradeEvent() {
    }

    public MarketTradeEvent(String marketSymbol, OffsetDateTime ts, BigDecimal price, BigDecimal qty, String side) {
        this.marketSymbol = marketSymbol;
        this.ts = ts;
        this.price = price;
        this.qty = qty;
        this.side = side;
    }

    public String getMarketSymbol() {
        return marketSymbol;
    }

    public void setMarketSymbol(String marketSymbol) {
        this.marketSymbol = marketSymbol;
    }

    public OffsetDateTime getTs() {
        return ts;
    }

    public void setTs(OffsetDateTime ts) {
        this.ts = ts;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }
}
