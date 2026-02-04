package com.cryptoexchange.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Simulated market trade data - separate from order matching trades.
 * Used for portfolio showcase and market data visualization.
 */
@Entity
@Table(name = "market_trade", indexes = {
    @Index(name = "idx_market_trade_symbol_ts", columnList = "market_symbol, ts DESC")
})
public class MarketTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "market_symbol", nullable = false, length = 20)
    private String marketSymbol;

    @Column(nullable = false)
    private OffsetDateTime ts;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private TradeSide side;

    public enum TradeSide {
        BUY, SELL
    }

    @PrePersist
    protected void onCreate() {
        if (ts == null) {
            ts = OffsetDateTime.now();
        }
    }

    // Constructors
    public MarketTrade() {
    }

    public MarketTrade(String marketSymbol, OffsetDateTime ts, BigDecimal price, BigDecimal qty, TradeSide side) {
        this.marketSymbol = marketSymbol;
        this.ts = ts;
        this.price = price;
        this.qty = qty;
        this.side = side;
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

    public TradeSide getSide() {
        return side;
    }

    public void setSide(TradeSide side) {
        this.side = side;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketTrade that = (MarketTrade) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
