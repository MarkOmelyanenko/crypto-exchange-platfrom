package com.cryptoexchange.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Market tick data for simulator - stores periodic price updates.
 * Separate from order matching trades.
 */
@Entity
@Table(name = "market_tick", indexes = {
    @Index(name = "idx_market_tick_symbol_ts", columnList = "market_symbol, ts DESC")
})
public class MarketTick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "market_symbol", nullable = false, length = 20)
    private String marketSymbol;

    @Column(nullable = false)
    private OffsetDateTime ts;

    @Column(name = "last_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal lastPrice;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal bid;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal ask;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal volume;

    @PrePersist
    protected void onCreate() {
        if (ts == null) {
            ts = OffsetDateTime.now();
        }
    }

    // Constructors
    public MarketTick() {
    }

    public MarketTick(String marketSymbol, OffsetDateTime ts, BigDecimal lastPrice, BigDecimal bid, BigDecimal ask, BigDecimal volume) {
        this.marketSymbol = marketSymbol;
        this.ts = ts;
        this.lastPrice = lastPrice;
        this.bid = bid;
        this.ask = ask;
        this.volume = volume;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketTick marketTick = (MarketTick) o;
        return Objects.equals(id, marketTick.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
