package com.cryptoexchange.backend.domain.service;

import java.util.List;

/**
 * Interface for storing/retrieving market snapshots (ticker and recent trades).
 * Allows decoupling from Redis for testing.
 */
public interface MarketSnapshotStore {
    void saveTicker(String marketSymbol, MarketSimulationEngine.TickData tickData);
    TickerSnapshot getTicker(String marketSymbol);
    
    void saveRecentTrades(String marketSymbol, List<MarketSimulationEngine.TradeData> trades);
    List<TradeSnapshot> getRecentTrades(String marketSymbol, int limit);
    
    // Data classes
    class TickerSnapshot {
        public java.time.OffsetDateTime ts;
        public java.math.BigDecimal last;
        public java.math.BigDecimal bid;
        public java.math.BigDecimal ask;
        public java.math.BigDecimal volume;
        
        public TickerSnapshot() {}
        
        public TickerSnapshot(java.time.OffsetDateTime ts, java.math.BigDecimal last, 
                             java.math.BigDecimal bid, java.math.BigDecimal ask, 
                             java.math.BigDecimal volume) {
            this.ts = ts;
            this.last = last;
            this.bid = bid;
            this.ask = ask;
            this.volume = volume;
        }
    }
    
    class TradeSnapshot {
        public java.time.OffsetDateTime ts;
        public java.math.BigDecimal price;
        public java.math.BigDecimal qty;
        public String side;
        
        public TradeSnapshot() {}
        
        public TradeSnapshot(java.time.OffsetDateTime ts, java.math.BigDecimal price, 
                            java.math.BigDecimal qty, String side) {
            this.ts = ts;
            this.price = price;
            this.qty = qty;
            this.side = side;
        }
    }
}
