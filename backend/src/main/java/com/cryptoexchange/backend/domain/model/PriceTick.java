package com.cryptoexchange.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores periodic Binance-fetched price snapshots.
 * Used for price history charts on the dashboard.
 */
@Entity
@Table(name = "price_tick", indexes = {
    @Index(name = "idx_price_tick_symbol_ts", columnList = "symbol, ts DESC")
})
public class PriceTick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "price_usd", nullable = false, precision = 38, scale = 18)
    private BigDecimal priceUsd;

    @Column(nullable = false)
    private OffsetDateTime ts;

    @PrePersist
    protected void onCreate() {
        if (ts == null) {
            ts = OffsetDateTime.now();
        }
    }

    public PriceTick() {
    }

    public PriceTick(String symbol, BigDecimal priceUsd, OffsetDateTime ts) {
        this.symbol = symbol;
        this.priceUsd = priceUsd;
        this.ts = ts;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getPriceUsd() {
        return priceUsd;
    }

    public void setPriceUsd(BigDecimal priceUsd) {
        this.priceUsd = priceUsd;
    }

    public OffsetDateTime getTs() {
        return ts;
    }

    public void setTs(OffsetDateTime ts) {
        this.ts = ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PriceTick priceTick = (PriceTick) o;
        return Objects.equals(id, priceTick.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
