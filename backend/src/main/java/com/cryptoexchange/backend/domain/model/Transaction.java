package com.cryptoexchange.backend.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a simple BUY/SELL transaction executed at market price.
 * Separate from the Order/Trade matching engine — this is the MVP "instant trade" model.
 */
@Entity
@Table(name = "transaction", indexes = {
    @Index(name = "idx_transaction_user_created", columnList = "user_id, created_at DESC"),
    @Index(name = "idx_transaction_user_symbol", columnList = "user_id, asset_symbol"),
    @Index(name = "idx_transaction_user_side", columnList = "user_id, side")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "asset_symbol", nullable = false, length = 10)
    private String assetSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "price_usd", nullable = false, precision = 38, scale = 18)
    private BigDecimal priceUsd;

    @Column(name = "total_usd", nullable = false, precision = 38, scale = 18)
    private BigDecimal totalUsd;

    @Column(name = "fee_usd", nullable = false, precision = 38, scale = 18)
    private BigDecimal feeUsd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (feeUsd == null) {
            feeUsd = BigDecimal.ZERO;
        }
    }

    // Constructors
    public Transaction() {
        this.feeUsd = BigDecimal.ZERO;
    }

    public Transaction(UserAccount user, String assetSymbol, OrderSide side,
                       BigDecimal quantity, BigDecimal priceUsd, BigDecimal totalUsd) {
        this();
        this.user = user;
        this.assetSymbol = assetSymbol;
        this.side = side;
        this.quantity = quantity;
        this.priceUsd = priceUsd;
        this.totalUsd = totalUsd;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }

    public String getAssetSymbol() { return assetSymbol; }
    public void setAssetSymbol(String assetSymbol) { this.assetSymbol = assetSymbol; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPriceUsd() { return priceUsd; }
    public void setPriceUsd(BigDecimal priceUsd) { this.priceUsd = priceUsd; }

    public BigDecimal getTotalUsd() { return totalUsd; }
    public void setTotalUsd(BigDecimal totalUsd) { this.totalUsd = totalUsd; }

    public BigDecimal getFeeUsd() { return feeUsd; }
    public void setFeeUsd(BigDecimal feeUsd) { this.feeUsd = feeUsd; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
