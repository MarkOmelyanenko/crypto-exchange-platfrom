package com.cryptoexchange.backend.domain.service;

import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Redis-based implementation of MarketSnapshotStore.
 */
@Component
public class RedisMarketSnapshotStore implements MarketSnapshotStore {
    
    private final MarketSimulatorRedisService redisService;
    
    public RedisMarketSnapshotStore(MarketSimulatorRedisService redisService) {
        this.redisService = redisService;
    }
    
    @Override
    public void saveTicker(String marketSymbol, MarketSimulationEngine.TickData tickData) {
        redisService.updateTicker(marketSymbol, tickData);
    }
    
    @Override
    public TickerSnapshot getTicker(String marketSymbol) {
        MarketSimulatorRedisService.TickerSnapshot snapshot = redisService.getTicker(marketSymbol);
        if (snapshot == null) {
            return null;
        }
        return new TickerSnapshot(snapshot.ts, snapshot.last, snapshot.bid, snapshot.ask, snapshot.volume);
    }
    
    @Override
    public void saveRecentTrades(String marketSymbol, List<MarketSimulationEngine.TradeData> trades) {
        redisService.updateRecentTrades(marketSymbol, trades);
    }
    
    @Override
    public List<TradeSnapshot> getRecentTrades(String marketSymbol, int limit) {
        List<MarketSimulatorRedisService.TradeSnapshot> snapshots = redisService.getRecentTrades(marketSymbol, limit);
        return snapshots.stream()
            .map(s -> new TradeSnapshot(s.ts, s.price, s.qty, s.side))
            .toList();
    }
}
