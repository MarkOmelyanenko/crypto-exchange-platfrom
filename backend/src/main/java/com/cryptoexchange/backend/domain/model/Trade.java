package com.cryptoexchange.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maker_order_id", nullable = false)
    private Order makerOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taker_order_id", nullable = false)
    private Order takerOrder;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "quote_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal quoteAmount;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = OffsetDateTime.now();
        }
    }

    // Constructors
    public Trade() {
    }

    public Trade(Market market, Order makerOrder, Order takerOrder, BigDecimal price, BigDecimal amount) {
        this.market = market;
        this.makerOrder = makerOrder;
        this.takerOrder = takerOrder;
        this.price = price;
        this.amount = amount;
        this.quoteAmount = price.multiply(amount);
    }

    public Trade(Market market, Order makerOrder, Order takerOrder, BigDecimal price, BigDecimal amount, BigDecimal quoteAmount) {
        this.market = market;
        this.makerOrder = makerOrder;
        this.takerOrder = takerOrder;
        this.price = price;
        this.amount = amount;
        this.quoteAmount = quoteAmount;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
    }

    public Order getMakerOrder() {
        return makerOrder;
    }

    public void setMakerOrder(Order makerOrder) {
        this.makerOrder = makerOrder;
    }

    public Order getTakerOrder() {
        return takerOrder;
    }

    public void setTakerOrder(Order takerOrder) {
        this.takerOrder = takerOrder;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    // Equals and HashCode based on ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return Objects.equals(id, trade.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
