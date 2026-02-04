package com.cryptoexchange.backend.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Kafka event for market tick updates.
 */
public class MarketTickEvent {
    private String marketSymbol;
    private OffsetDateTime ts;
    private BigDecimal lastPrice;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal volume;

    public MarketTickEvent() {
    }

    public MarketTickEvent(String marketSymbol, OffsetDateTime ts, BigDecimal lastPrice, 
                          BigDecimal bid, BigDecimal ask, BigDecimal volume) {
        this.marketSymbol = marketSymbol;
        this.ts = ts;
        this.lastPrice = lastPrice;
        this.bid = bid;
        this.ask = ask;
        this.volume = volume;
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

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }
}
