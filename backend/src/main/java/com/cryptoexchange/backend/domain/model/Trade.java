package com.cryptoexchange.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Records a filled market order (instant spot trade).
 */
@Entity
@Table(name = "trade", indexes = {
    @Index(name = "idx_trade_user_created", columnList = "user_id, created_at DESC"),
    @Index(name = "idx_trade_pair_created", columnList = "pair_id, created_at DESC")
})
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair_id", nullable = false)
    private Market pair;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "base_qty", nullable = false, precision = 38, scale = 18)
    private BigDecimal baseQty;

    @Column(name = "quote_qty", nullable = false, precision = 38, scale = 18)
    private BigDecimal quoteQty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // Constructors
    public Trade() {}

    public Trade(UserAccount user, Market pair, OrderSide side,
                 BigDecimal price, BigDecimal baseQty, BigDecimal quoteQty) {
        this.user = user;
        this.pair = pair;
        this.side = side;
        this.price = price;
        this.baseQty = baseQty;
        this.quoteQty = quoteQty;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }

    public Market getPair() { return pair; }
    public void setPair(Market pair) { this.pair = pair; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getBaseQty() { return baseQty; }
    public void setBaseQty(BigDecimal baseQty) { this.baseQty = baseQty; }

    public BigDecimal getQuoteQty() { return quoteQty; }
    public void setQuoteQty(BigDecimal quoteQty) { this.quoteQty = quoteQty; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

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
