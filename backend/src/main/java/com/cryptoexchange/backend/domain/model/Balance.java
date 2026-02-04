package com.cryptoexchange.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "balance", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "asset_id"})
})
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    @JsonIgnore
    private Asset asset;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal available;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal locked;

    @Version
    @Column(nullable = false)
    @JsonIgnore
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public Balance() {
        this.available = BigDecimal.ZERO;
        this.locked = BigDecimal.ZERO;
        this.version = 0;
    }

    public Balance(UserAccount user, Asset asset) {
        this();
        this.user = user;
        this.asset = asset;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public void setAvailable(BigDecimal available) {
        this.available = available;
    }

    public BigDecimal getLocked() {
        return locked;
    }

    public void setLocked(BigDecimal locked) {
        this.locked = locked;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods for JSON serialization
    @JsonProperty("currency")
    @Schema(description = "Currency symbol", example = "USDT", accessMode = Schema.AccessMode.READ_ONLY, type = "string")
    public String getCurrency() {
        if (asset == null) {
            return null;
        }
        try {
            return asset.getSymbol();
        } catch (Exception e) {
            // Handle lazy loading issues gracefully during schema generation or when outside transaction
            // This can happen when SpringDoc tries to introspect the entity outside of a transaction
            return null;
        }
    }

    // Equals and HashCode based on ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Balance balance = (Balance) o;
        return Objects.equals(id, balance.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
